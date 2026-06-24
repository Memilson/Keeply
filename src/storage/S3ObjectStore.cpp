#include "keeply/S3ObjectStore.hpp"
#include "keeply/Hash.hpp"
#include "keeply/Util.hpp"

#include <algorithm>
#include <curl/curl.h>
#include <iomanip>
#include <sstream>
#include <stdexcept>

namespace keeply {

static std::string uriEncodePath(const std::string& s) {
    std::ostringstream os;
    os << std::uppercase << std::hex;
    for (unsigned char c : s) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' || c == '/') {
            os << c;
        } else {
            os << '%' << std::setw(2) << std::setfill('0') << static_cast<int>(c);
        }
    }
    return os.str();
}

static size_t writeCallback(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* out = static_cast<std::vector<unsigned char>*>(userdata);
    const auto total = size * nmemb;
    out->insert(out->end(), reinterpret_cast<unsigned char*>(ptr), reinterpret_cast<unsigned char*>(ptr) + total);
    return total;
}

S3ObjectStore::S3ObjectStore(RepositoryConfig cfg) : cfg_(std::move(cfg)), url_(parseEndpoint(cfg_.endpoint)) {
    curl_global_init(CURL_GLOBAL_DEFAULT);
    if (cfg_.endpoint.empty() || cfg_.bucket.empty() || cfg_.accessKey.empty() || cfg_.secretKey.empty()) {
        throw std::runtime_error("s3 config requires endpoint, bucket, access_key and secret_key");
    }
}

S3ObjectStore::~S3ObjectStore() {
    curl_global_cleanup();
}

S3ObjectStore::UrlParts S3ObjectStore::parseEndpoint(const std::string& endpoint) {
    UrlParts u;
    auto pos = endpoint.find("://");
    if (pos == std::string::npos) throw std::runtime_error("invalid endpoint: " + endpoint);
    u.scheme = endpoint.substr(0, pos);
    std::string rest = endpoint.substr(pos + 3);
    while (!rest.empty() && rest.back() == '/') rest.pop_back();
    auto slash = rest.find('/');
    if (slash != std::string::npos) rest = rest.substr(0, slash);
    auto colon = rest.rfind(':');
    if (colon != std::string::npos) {
        u.host = rest.substr(0, colon);
        u.port = rest.substr(colon + 1);
    } else {
        u.host = rest;
    }
    u.baseUrl = u.scheme + "://" + rest;
    return u;
}

std::string S3ObjectStore::hostHeader() const {
    if (!url_.port.empty()) return url_.host + ":" + url_.port;
    return url_.host;
}

std::string S3ObjectStore::fullKey(const std::string& key) const {
    return joinObjectKey(cfg_.prefix, key);
}

std::string S3ObjectStore::objectUrl(const std::string& key) const {
    return url_.baseUrl + "/" + cfg_.bucket + "/" + uriEncodePath(fullKey(key));
}

std::string S3ObjectStore::authorizationHeader(const std::string& method, const std::string& canonicalUri, const std::string& payloadHash, const std::string& amzDate, const std::string& dateStamp) const {
    const std::string service = "s3";
    const std::string credentialScope = dateStamp + "/" + cfg_.region + "/" + service + "/aws4_request";
    const std::string signedHeaders = "host;x-amz-content-sha256;x-amz-date";
    const std::string canonicalHeaders =
        "host:" + hostHeader() + "\n" +
        "x-amz-content-sha256:" + payloadHash + "\n" +
        "x-amz-date:" + amzDate + "\n";

    const std::string canonicalRequest =
        method + "\n" +
        canonicalUri + "\n" +
        "\n" +
        canonicalHeaders + "\n" +
        signedHeaders + "\n" +
        payloadHash;

    const auto canonicalHash = sha256Hex(canonicalRequest);
    const std::string stringToSign =
        "AWS4-HMAC-SHA256\n" +
        amzDate + "\n" +
        credentialScope + "\n" +
        canonicalHash;

    auto kDate = hmacSha256("AWS4" + cfg_.secretKey, dateStamp);
    auto kRegion = hmacSha256(kDate, cfg_.region);
    auto kService = hmacSha256(kRegion, service);
    auto kSigning = hmacSha256(kService, "aws4_request");
    auto sigBytes = hmacSha256(kSigning, stringToSign);
    const auto signature = toHex(sigBytes.data(), sigBytes.size());

    return "Authorization: AWS4-HMAC-SHA256 Credential=" + cfg_.accessKey + "/" + credentialScope +
           ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
}

long S3ObjectStore::request(const std::string& method, const std::string& key, const std::vector<unsigned char>* body, std::vector<unsigned char>* response) {
    const std::vector<unsigned char> empty;
    const auto& payload = body ? *body : empty;
    const auto payloadHash = sha256Hex(payload);
    const auto amzDate = amzDateUtc();
    const auto dateStamp = amzDate.substr(0, 8);
    const auto canonicalUri = "/" + cfg_.bucket + "/" + uriEncodePath(fullKey(key));
    const auto auth = authorizationHeader(method, canonicalUri, payloadHash, amzDate, dateStamp);

    CURL* curl = curl_easy_init();
    if (!curl) throw std::runtime_error("curl_easy_init failed");

    struct curl_slist* headers = nullptr;
    const auto host = "Host: " + hostHeader();
    const auto xdate = "x-amz-date: " + amzDate;
    const auto xhash = "x-amz-content-sha256: " + payloadHash;
    headers = curl_slist_append(headers, host.c_str());
    headers = curl_slist_append(headers, xdate.c_str());
    headers = curl_slist_append(headers, xhash.c_str());
    headers = curl_slist_append(headers, auth.c_str());

    std::vector<unsigned char> localResponse;
    curl_easy_setopt(curl, CURLOPT_URL, objectUrl(key).c_str());
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, response ? response : &localResponse);

    if (method == "HEAD") {
        curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
    } else if (method == "PUT") {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "PUT");
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, payload.empty() ? "" : reinterpret_cast<const char*>(payload.data()));
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE_LARGE, static_cast<curl_off_t>(payload.size()));
    } else if (method == "GET") {
        curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    } else if (method == "DELETE") {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "DELETE");
    } else {
        curl_slist_free_all(headers);
        curl_easy_cleanup(curl);
        throw std::runtime_error("unsupported s3 method: " + method);
    }

    CURLcode res = curl_easy_perform(curl);
    long code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &code);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    if (res != CURLE_OK) throw std::runtime_error(std::string("curl failed: ") + curl_easy_strerror(res));
    return code;
}

bool S3ObjectStore::exists(const std::string& key) {
    const auto code = request("HEAD", key, nullptr, nullptr);
    if (code == 200) return true;
    if (code == 404) return false;
    throw std::runtime_error("s3 HEAD failed for " + key + ", http " + std::to_string(code));
}

void S3ObjectStore::put(const std::string& key, const std::vector<unsigned char>& data) {
    const auto code = request("PUT", key, &data, nullptr);
    if (code < 200 || code >= 300) throw std::runtime_error("s3 PUT failed for " + key + ", http " + std::to_string(code));
}

std::vector<unsigned char> S3ObjectStore::get(const std::string& key) {
    std::vector<unsigned char> out;
    const auto code = request("GET", key, nullptr, &out);
    if (code != 200) throw std::runtime_error("s3 GET failed for " + key + ", http " + std::to_string(code));
    return out;
}

void S3ObjectStore::remove(const std::string& key) {
    const auto code = request("DELETE", key, nullptr, nullptr);
    if (code != 200 && code != 204 && code != 404) throw std::runtime_error("s3 DELETE failed for " + key + ", http " + std::to_string(code));
}

}
