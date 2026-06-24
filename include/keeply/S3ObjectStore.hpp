#pragma once

#include "keeply/Config.hpp"
#include "keeply/ObjectStore.hpp"

#include <string>

namespace keeply {

class S3ObjectStore final : public ObjectStore {
public:
    explicit S3ObjectStore(RepositoryConfig cfg);
    ~S3ObjectStore() override;

    bool exists(const std::string& key) override;
    void put(const std::string& key, const std::vector<unsigned char>& data) override;
    std::vector<unsigned char> get(const std::string& key) override;
    void remove(const std::string& key) override;

private:
    struct UrlParts {
        std::string scheme;
        std::string host;
        std::string port;
        std::string baseUrl;
    };

    RepositoryConfig cfg_;
    UrlParts url_;

    static UrlParts parseEndpoint(const std::string& endpoint);
    std::string fullKey(const std::string& key) const;
    std::string objectUrl(const std::string& key) const;
    std::string hostHeader() const;
    std::string authorizationHeader(const std::string& method, const std::string& canonicalUri, const std::string& payloadHash, const std::string& amzDate, const std::string& dateStamp) const;
    long request(const std::string& method, const std::string& key, const std::vector<unsigned char>* body, std::vector<unsigned char>* response);
};

}
