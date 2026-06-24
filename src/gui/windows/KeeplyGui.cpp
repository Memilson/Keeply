#include "keeply/BackupEngine.hpp"
#include "keeply/Config.hpp"
#include "keeply/Crypto.hpp"
#include "keeply/LocalDb.hpp"
#include "keeply/PruneEngine.hpp"
#include "keeply/RestoreEngine.hpp"
#include "keeply/Util.hpp"
#include "keeply/VerifyEngine.hpp"

#include <windows.h>
#include <commctrl.h>
#include <shellapi.h>
#include <shlobj.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <filesystem>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

using namespace keeply;

constexpr int ID_CONFIG=100,ID_DB=101,ID_TYPE=102,ID_LOCAL=103,ID_ENDPOINT=104,ID_BUCKET=105,ID_ACCESS=106,ID_SECRET=107,ID_REGION=108,ID_PREFIX=109,ID_ENC=110,ID_KEY=111,ID_POLL=112;
constexpr int ID_LOAD=200,ID_SAVE=201,ID_INIT_LOCAL=202,ID_INIT_S3=203,ID_JOB_NAME=204,ID_SOURCE=205,ID_INTERVAL=206,ID_KEEP=207,ID_ADD_JOB=208,ID_REMOVE_JOB=209,ID_RUN_JOB=210,ID_START=211,ID_STOP=212;
constexpr int ID_JOBS=300,ID_SNAPSHOTS=301,ID_TARGET=302,ID_BACKUP=303,ID_REFRESH=304,ID_VERIFY=305,ID_RESTORE=306,ID_PRUNE=307,ID_LOG=308,ID_BACKUP_SOURCE=309,ID_RESTORE_SNAPSHOT=310;
constexpr int ID_NAV_BACKUP=400,ID_NAV_RESTORE=401,ID_NAV_JOBS=402,ID_NAV_SNAPSHOTS=403,ID_NAV_CONFIG=404,ID_PICK_BACKUP=405,ID_PICK_RESTORE=406,ID_NAV_FILES=407,ID_FILES_SNAPSHOT=408,ID_FILES_LOAD=409,ID_FILE_TREE=410;
constexpr UINT WM_LOG=WM_APP+1,WM_REFRESH=WM_APP+2;

static HWND g_hwnd{},g_config{},g_db{},g_type{},g_local{},g_endpoint{},g_bucket{},g_access{},g_secret{},g_region{},g_prefix{},g_enc{},g_key{},g_poll{},g_jobName{},g_source{},g_interval{},g_keep{},g_jobs{},g_snaps{},g_target{},g_log{},g_status{},g_backupSource{},g_restoreSnapshot{},g_filesSnapshot{},g_filesTree{};
static HFONT g_font{},g_titleFont{};
static HBRUSH g_bgBrush{};
static Config g_cfg;
static std::filesystem::path g_configPath{"keeply.local.json"};
static std::atomic_bool g_agentStop{true};
static std::thread g_agentThread;
static std::vector<HWND> g_backupView,g_restoreView,g_jobsView,g_snapshotsView,g_filesView,g_configView;

static HMENU menuId(int id){return reinterpret_cast<HMENU>(static_cast<INT_PTR>(id));}

static void remember(std::vector<HWND>* group,HWND h){if(group)group->push_back(h);}

static std::string wideToUtf8(const std::wstring& value){
    if(value.empty())return{};
    int len=WideCharToMultiByte(CP_UTF8,0,value.data(),static_cast<int>(value.size()),nullptr,0,nullptr,nullptr);
    std::string out(static_cast<std::size_t>(len),'\0');
    WideCharToMultiByte(CP_UTF8,0,value.data(),static_cast<int>(value.size()),out.data(),len,nullptr,nullptr);
    return out;
}

static std::string text(HWND h){
    int len=GetWindowTextLengthA(h);
    std::string out(static_cast<std::size_t>(len),'\0');
    GetWindowTextA(h,out.data(),len+1);
    return out;
}

static void setText(HWND h,const std::string& value){SetWindowTextA(h,value.c_str());}
static int intText(HWND h,int fallback){try{return std::stoi(text(h));}catch(...){return fallback;}}

static HWND addLabel(HWND parent,const char* value,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("STATIC",value,WS_CHILD|WS_VISIBLE,x,y,w,h,parent,nullptr,GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addTitle(HWND parent,const char* value,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("STATIC",value,WS_CHILD|WS_VISIBLE,x,y,w,h,parent,nullptr,GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_titleFont),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addEdit(HWND parent,int id,int x,int y,int w,int h,DWORD extra=0,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowExA(WS_EX_CLIENTEDGE,"EDIT","",WS_CHILD|WS_VISIBLE|ES_AUTOHSCROLL|extra,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addButton(HWND parent,int id,const char* value,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("BUTTON",value,WS_CHILD|WS_VISIBLE|BS_PUSHBUTTON,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addList(HWND parent,int id,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowExA(WS_EX_CLIENTEDGE,"LISTBOX","",WS_CHILD|WS_VISIBLE|WS_VSCROLL|LBS_NOTIFY,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addTree(HWND parent,int id,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowExA(WS_EX_CLIENTEDGE,WC_TREEVIEWA,"",WS_CHILD|WS_VISIBLE|WS_VSCROLL|WS_HSCROLL|TVS_HASBUTTONS|TVS_HASLINES|TVS_LINESATROOT,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addGroup(HWND parent,const char* value,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("BUTTON",value,WS_CHILD|WS_VISIBLE|BS_GROUPBOX,x,y,w,h,parent,nullptr,GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addCombo(HWND parent,int id,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("COMBOBOX","",WS_CHILD|WS_VISIBLE|CBS_DROPDOWNLIST,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static HWND addCheck(HWND parent,int id,const char* value,int x,int y,int w,int h,std::vector<HWND>* group=nullptr){
    HWND ctl=CreateWindowA("BUTTON",value,WS_CHILD|WS_VISIBLE|BS_AUTOCHECKBOX,x,y,w,h,parent,menuId(id),GetModuleHandleA(nullptr),nullptr);
    SendMessageA(ctl,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
    remember(group,ctl);
    return ctl;
}

static std::string pickFolder(HWND owner){
    BROWSEINFOA bi{};
    bi.hwndOwner=owner;
    bi.ulFlags=BIF_RETURNONLYFSDIRS|BIF_NEWDIALOGSTYLE;
    bi.lpszTitle="Selecione uma pasta";
    PIDLIST_ABSOLUTE pid=SHBrowseForFolderA(&bi);
    if(!pid)return{};
    char path[MAX_PATH]{};
    SHGetPathFromIDListA(pid,path);
    CoTaskMemFree(pid);
    return path;
}

static void showGroup(const std::vector<HWND>& group,bool show){
    for(HWND h:group)ShowWindow(h,show?SW_SHOW:SW_HIDE);
}

static void showModule(int id){
    showGroup(g_backupView,false);
    showGroup(g_restoreView,false);
    showGroup(g_jobsView,false);
    showGroup(g_snapshotsView,false);
    showGroup(g_filesView,false);
    showGroup(g_configView,false);
    if(id==ID_NAV_BACKUP)showGroup(g_backupView,true);
    else if(id==ID_NAV_RESTORE)showGroup(g_restoreView,true);
    else if(id==ID_NAV_JOBS)showGroup(g_jobsView,true);
    else if(id==ID_NAV_SNAPSHOTS)showGroup(g_snapshotsView,true);
    else if(id==ID_NAV_FILES)showGroup(g_filesView,true);
    else showGroup(g_configView,true);
}

static void postLog(const std::string& value){
    PostMessageA(g_hwnd,WM_LOG,0,reinterpret_cast<LPARAM>(new std::string(value)));
}

static void appendLog(const std::string& value){
    std::string msg=value+"\r\n";
    int len=GetWindowTextLengthA(g_log);
    SendMessageA(g_log,EM_SETSEL,static_cast<WPARAM>(len),static_cast<LPARAM>(len));
    SendMessageA(g_log,EM_REPLACESEL,FALSE,reinterpret_cast<LPARAM>(msg.c_str()));
}

static void refreshJobs(){
    SendMessageA(g_jobs,LB_RESETCONTENT,0,0);
    for(const auto& job:g_cfg.jobs){
        std::string row=job.name+" | "+(job.enabled?"ativo":"inativo")+" | "+std::to_string(job.intervalMinutes)+"min | keep="+std::to_string(job.retentionKeepLast)+" | "+job.source;
        SendMessageA(g_jobs,LB_ADDSTRING,0,reinterpret_cast<LPARAM>(row.c_str()));
    }
}

static std::string snapshotIdFromRow(const std::string& row){
    auto p=row.find(" | ");
    return p==std::string::npos?row:row.substr(0,p);
}

static std::string selectedText(HWND list);

static HTREEITEM addTreeItem(HWND tree,HTREEITEM parent,const std::string& value){
    TVINSERTSTRUCTA item{};
    item.hParent=parent;
    item.hInsertAfter=TVI_LAST;
    item.item.mask=TVIF_TEXT;
    item.item.pszText=const_cast<char*>(value.c_str());
    return reinterpret_cast<HTREEITEM>(SendMessageA(tree,TVM_INSERTITEMA,0,reinterpret_cast<LPARAM>(&item)));
}

static std::vector<std::string> splitPath(std::string path){
    std::replace(path.begin(),path.end(),'\\','/');
    std::vector<std::string> out;
    std::size_t start=0;
    while(start<path.size()){
        auto end=path.find('/',start);
        auto part=path.substr(start,end==std::string::npos?std::string::npos:end-start);
        if(!part.empty())out.push_back(part);
        if(end==std::string::npos)break;
        start=end+1;
    }
    return out;
}

static void loadSnapshotFiles(){
    auto snap=text(g_filesSnapshot);
    if(snap.empty())snap=snapshotIdFromRow(selectedText(g_snaps));
    LocalDb db(g_cfg.dbPath);
    db.migrate();
    if(snap.empty()||snap=="latest"){
        auto latest=db.getSnapshot("latest");
        if(!latest)throw std::runtime_error("snapshot nao encontrada");
        snap=latest->id;
    }
    auto files=db.listSnapshotFiles(snap);
    setText(g_filesSnapshot,snap);
    TreeView_DeleteAllItems(g_filesTree);
    auto root=addTreeItem(g_filesTree,TVI_ROOT,snap+" | "+std::to_string(files.size())+" arquivos");
    std::map<std::string,HTREEITEM> folders;
    for(const auto& file:files){
        auto parts=splitPath(file.path);
        if(parts.empty())continue;
        HTREEITEM parent=root;
        std::string prefix;
        for(std::size_t i=0;i+1<parts.size();++i){
            if(!prefix.empty())prefix+="/";
            prefix+=parts[i];
            auto it=folders.find(prefix);
            if(it==folders.end()){
                parent=addTreeItem(g_filesTree,parent,parts[i]);
                folders[prefix]=parent;
            }else{
                parent=it->second;
            }
        }
        addTreeItem(g_filesTree,parent,parts.back()+" ("+std::to_string(file.size)+" bytes)");
    }
    TreeView_Expand(g_filesTree,root,TVE_EXPAND);
    appendLog("arquivos carregados: "+std::to_string(files.size()));
}

static void refreshSnapshots(){
    SendMessageA(g_snaps,LB_RESETCONTENT,0,0);
    try{
        LocalDb db(g_cfg.dbPath);
        db.migrate();
        bool first=true;
        for(const auto& s:db.listSnapshots()){
            std::string row=s.id+" | "+s.createdAt+" | "+s.status+" | files="+std::to_string(s.totalFiles)+" | bytes="+std::to_string(s.totalBytes);
            int idx=static_cast<int>(SendMessageA(g_snaps,LB_ADDSTRING,0,reinterpret_cast<LPARAM>(row.c_str())));
            if(first){
                SendMessageA(g_snaps,LB_SETCURSEL,idx,0);
                setText(g_restoreSnapshot,s.id);
                setText(g_filesSnapshot,s.id);
                first=false;
            }
        }
    }catch(const std::exception& e){
        appendLog(std::string("snapshots: ")+e.what());
    }
}

static void fillUi(){
    setText(g_config,g_configPath.string());
    setText(g_db,g_cfg.dbPath);
    SendMessageA(g_type,CB_RESETCONTENT,0,0);
    SendMessageA(g_type,CB_ADDSTRING,0,reinterpret_cast<LPARAM>("local"));
    SendMessageA(g_type,CB_ADDSTRING,0,reinterpret_cast<LPARAM>("s3"));
    SendMessageA(g_type,CB_SELECTSTRING,static_cast<WPARAM>(-1),reinterpret_cast<LPARAM>(g_cfg.repository.type.c_str()));
    setText(g_local,g_cfg.repository.path);
    setText(g_endpoint,g_cfg.repository.endpoint);
    setText(g_bucket,g_cfg.repository.bucket);
    setText(g_access,g_cfg.repository.accessKey);
    setText(g_secret,g_cfg.repository.secretKey);
    setText(g_region,g_cfg.repository.region);
    setText(g_prefix,g_cfg.repository.prefix);
    SendMessageA(g_enc,BM_SETCHECK,g_cfg.encryption.enabled?BST_CHECKED:BST_UNCHECKED,0);
    setText(g_key,g_cfg.encryption.keyHex);
    setText(g_poll,std::to_string(g_cfg.agent.pollSeconds));
    setText(g_status,"Config: "+g_configPath.string()+" | "+g_cfg.repository.type+" | "+g_cfg.dbPath);
    refreshJobs();
    refreshSnapshots();
}

static Config uiConfig(){
    Config c=g_cfg;
    c.dbPath=text(g_db);
    int idx=static_cast<int>(SendMessageA(g_type,CB_GETCURSEL,0,0));
    char type[32]{};
    if(idx>=0)SendMessageA(g_type,CB_GETLBTEXT,static_cast<WPARAM>(idx),reinterpret_cast<LPARAM>(type));
    c.repository.type=type[0]?type:"local";
    c.repository.path=text(g_local);
    c.repository.endpoint=text(g_endpoint);
    c.repository.bucket=text(g_bucket);
    c.repository.accessKey=text(g_access);
    c.repository.secretKey=text(g_secret);
    c.repository.region=text(g_region).empty()?"us-east-1":text(g_region);
    c.repository.prefix=text(g_prefix);
    c.encryption.enabled=SendMessageA(g_enc,BM_GETCHECK,0,0)==BST_CHECKED;
    c.encryption.keyHex=text(g_key);
    if(c.encryption.enabled&&c.encryption.keyHex.empty())c.encryption.keyHex=generateEncryptionKeyHex();
    c.agent.pollSeconds=std::max(5,intText(g_poll,30));
    return c;
}

static void saveConfig(){
    g_configPath=text(g_config);
    g_cfg=uiConfig();
    g_cfg.save(g_configPath);
    LocalDb db(g_cfg.dbPath);
    db.migrate();
    fillUi();
    appendLog("config salva");
}

static void loadConfig(){
    g_configPath=text(g_config);
    g_cfg=Config::load(g_configPath);
    fillUi();
    appendLog("config carregada");
}

static int selectedIndex(HWND list){
    return static_cast<int>(SendMessageA(list,LB_GETCURSEL,0,0));
}

static std::string selectedText(HWND list){
    int idx=selectedIndex(list);
    if(idx<0)return{};
    int len=static_cast<int>(SendMessageA(list,LB_GETTEXTLEN,static_cast<WPARAM>(idx),0));
    std::string out(static_cast<std::size_t>(len),'\0');
    SendMessageA(list,LB_GETTEXT,static_cast<WPARAM>(idx),reinterpret_cast<LPARAM>(out.data()));
    return out;
}

template<class Fn>
static void runAsync(std::string label,Fn fn){
    std::thread([label=std::move(label),fn=std::move(fn)]()mutable{
        try{
            postLog(label+" iniciado");
            fn();
            postLog(label+" concluido");
            PostMessageA(g_hwnd,WM_REFRESH,0,0);
        }catch(const std::exception& e){
            postLog(label+" erro: "+std::string(e.what()));
        }
    }).detach();
}

static bool due(const JobConfig& job){
    if(job.lastRunAt.empty())return true;
    auto last=utcIsoToUnix(job.lastRunAt);
    if(last<=0)return true;
    return nowUnix()-last>=static_cast<std::int64_t>(std::max(1,job.intervalMinutes))*60;
}

static void runOneJob(std::filesystem::path configPath,std::string name){
    auto cfg=Config::load(configPath);
    for(auto& job:cfg.jobs){
        if(job.name!=name)continue;
        BackupEngine backup(cfg);
        auto r=backup.run(job.source);
        job.lastRunAt=nowUtcIso();
        cfg.save(configPath);
        if(job.retentionKeepLast>=0){
            PruneEngine prune(cfg);
            prune.keepLast(job.retentionKeepLast,std::filesystem::absolute(job.source).string());
        }
        postLog("snapshot "+r.snapshotId);
        return;
    }
    throw std::runtime_error("job nao encontrado");
}

static void startAgent(){
    if(!g_agentStop)return;
    saveConfig();
    g_agentStop=false;
    auto configPath=g_configPath;
    g_agentThread=std::thread([configPath](){
        postLog("agente iniciado");
        while(!g_agentStop){
            try{
                auto cfg=Config::load(configPath);
                bool ran=false;
                for(auto& job:cfg.jobs){
                    if(g_agentStop)break;
                    if(!job.enabled||!due(job))continue;
                    BackupEngine backup(cfg);
                    auto r=backup.run(job.source);
                    job.lastRunAt=nowUtcIso();
                    cfg.save(configPath);
                    if(job.retentionKeepLast>=0){
                        PruneEngine prune(cfg);
                        prune.keepLast(job.retentionKeepLast,std::filesystem::absolute(job.source).string());
                    }
                    postLog("job "+job.name+" "+r.snapshotId);
                    ran=true;
                }
                if(ran)PostMessageA(g_hwnd,WM_REFRESH,0,0);
                int sleepSeconds=std::max(5,cfg.agent.pollSeconds);
                for(int i=0;i<sleepSeconds&&!g_agentStop;i++)std::this_thread::sleep_for(std::chrono::seconds(1));
            }catch(const std::exception& e){
                postLog(std::string("agente erro: ")+e.what());
                for(int i=0;i<10&&!g_agentStop;i++)std::this_thread::sleep_for(std::chrono::seconds(1));
            }
        }
        postLog("agente parado");
    });
}

static void stopAgent(){
    g_agentStop=true;
    if(g_agentThread.joinable())g_agentThread.join();
}

static void addJob(){
    g_cfg=uiConfig();
    JobConfig job;
    job.name=text(g_jobName);
    job.source=text(g_source);
    job.intervalMinutes=std::max(1,intText(g_interval,60));
    job.retentionKeepLast=std::max(0,intText(g_keep,10));
    if(job.name.empty()||job.source.empty())throw std::runtime_error("job precisa de nome e origem");
    auto it=std::find_if(g_cfg.jobs.begin(),g_cfg.jobs.end(),[&](const JobConfig& j){return j.name==job.name;});
    if(it==g_cfg.jobs.end())g_cfg.jobs.push_back(job);else *it=job;
    saveConfig();
    appendLog("job salvo");
}

static void removeJob(){
    int idx=selectedIndex(g_jobs);
    if(idx<0||idx>=static_cast<int>(g_cfg.jobs.size()))return;
    auto name=g_cfg.jobs[static_cast<std::size_t>(idx)].name;
    g_cfg.jobs.erase(g_cfg.jobs.begin()+idx);
    saveConfig();
    appendLog("job removido: "+name);
}

static void initConfig(bool s3){
    g_cfg=uiConfig();
    g_cfg.repository.type=s3?"s3":"local";
    if(g_cfg.dbPath.empty()||s3&&g_cfg.dbPath=="./keeply.db")g_cfg.dbPath=s3?"./keeply.minio.db":"./keeply.db";
    if(!s3&&g_cfg.repository.path.empty())g_cfg.repository.path="./repo";
    if(s3&&g_cfg.repository.endpoint.empty())g_cfg.repository.endpoint="http://localhost:9000";
    if(s3&&g_cfg.repository.bucket.empty())g_cfg.repository.bucket="keeply";
    if(s3&&g_cfg.repository.accessKey.empty())g_cfg.repository.accessKey="keeply";
    if(s3&&g_cfg.repository.secretKey.empty())g_cfg.repository.secretKey="keeply123456";
    if(s3&&g_cfg.repository.region.empty())g_cfg.repository.region="us-east-1";
    if(s3&&(g_cfg.repository.prefix.empty()||g_cfg.repository.prefix=="repos/default"))g_cfg.repository.prefix="repos/dev-machine";
    g_cfg.save(text(g_config));
    LocalDb db(g_cfg.dbPath);
    db.migrate();
    fillUi();
    appendLog(s3?"MinIO configurado":"local configurado");
}

static void command(int id){
    if(id==ID_NAV_BACKUP||id==ID_NAV_RESTORE||id==ID_NAV_JOBS||id==ID_NAV_SNAPSHOTS||id==ID_NAV_FILES||id==ID_NAV_CONFIG)showModule(id);
    else if(id==ID_PICK_BACKUP){auto p=pickFolder(g_hwnd);if(!p.empty())setText(g_backupSource,p);}
    else if(id==ID_PICK_RESTORE){auto p=pickFolder(g_hwnd);if(!p.empty())setText(g_target,p);}
    else if(id==ID_LOAD)loadConfig();
    else if(id==ID_SAVE)saveConfig();
    else if(id==ID_INIT_LOCAL)initConfig(false);
    else if(id==ID_INIT_S3)initConfig(true);
    else if(id==ID_FILES_LOAD)loadSnapshotFiles();
    else if(id==ID_ADD_JOB)addJob();
    else if(id==ID_REMOVE_JOB)removeJob();
    else if(id==ID_RUN_JOB){
        int idx=selectedIndex(g_jobs);
        if(idx<0||idx>=static_cast<int>(g_cfg.jobs.size()))throw std::runtime_error("selecione um job");
        saveConfig();
        auto name=g_cfg.jobs[static_cast<std::size_t>(idx)].name;
        auto path=g_configPath;
        runAsync("job "+name,[path,name](){runOneJob(path,name);});
    }else if(id==ID_START)startAgent();
    else if(id==ID_STOP)stopAgent();
    else if(id==ID_BACKUP){
        auto cfg=uiConfig();
        auto source=text(g_backupSource);
        if(source.empty())throw std::runtime_error("origem vazia");
        runAsync("backup",[cfg,source](){BackupEngine b(cfg);auto r=b.run(source);postLog("snapshot "+r.snapshotId);});
    }else if(id==ID_REFRESH){
        g_cfg=uiConfig();
        refreshSnapshots();
    }else if(id==ID_VERIFY){
        auto cfg=uiConfig();
        auto snap=text(g_restoreSnapshot);
        if(snap.empty())snap=snapshotIdFromRow(selectedText(g_snaps));
        if(snap.empty())snap="latest";
        runAsync("verify",[cfg,snap](){VerifyEngine v(cfg);auto r=v.run(snap);postLog("errors "+std::to_string(r.errors));});
    }else if(id==ID_RESTORE){
        auto cfg=uiConfig();
        auto snap=text(g_restoreSnapshot);
        auto target=text(g_target);
        if(snap.empty())snap=snapshotIdFromRow(selectedText(g_snaps));
        if(snap.empty())snap="latest";
        if(target.empty())throw std::runtime_error("target vazio");
        runAsync("restore",[cfg,snap,target](){RestoreEngine r(cfg);auto out=r.run(snap,target);postLog("files "+std::to_string(out.files));});
    }else if(id==ID_PRUNE){
        auto cfg=uiConfig();
        int keep=std::max(0,intText(g_keep,10));
        runAsync("prune",[cfg,keep](){PruneEngine p(cfg);auto r=p.keepLast(keep);postLog("snapshots "+std::to_string(r.snapshotsDeleted)+" chunks "+std::to_string(r.chunksDeleted));});
    }
}

static LRESULT CALLBACK wndProc(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp){
    switch(msg){
    case WM_CREATE:{
        INITCOMMONCONTROLSEX icc{sizeof(icc),ICC_TREEVIEW_CLASSES};
        InitCommonControlsEx(&icc);
        g_hwnd=hwnd;
        g_bgBrush=CreateSolidBrush(RGB(247,249,252));
        g_font=CreateFontA(16,0,0,0,FW_NORMAL,FALSE,FALSE,FALSE,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,DEFAULT_PITCH,"Segoe UI");
        g_titleFont=CreateFontA(24,0,0,0,FW_SEMIBOLD,FALSE,FALSE,FALSE,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,DEFAULT_PITCH,"Segoe UI");
        addTitle(hwnd,"Keeply",18,16,160,32);
        g_status=addLabel(hwnd,"",190,22,780,24);
        addButton(hwnd,ID_NAV_BACKUP,"Backup",18,62,104,34);
        addButton(hwnd,ID_NAV_RESTORE,"Restore",132,62,104,34);
        addButton(hwnd,ID_NAV_SNAPSHOTS,"Snapshots",246,62,112,34);
        addButton(hwnd,ID_NAV_FILES,"Arquivos",368,62,104,34);
        addButton(hwnd,ID_NAV_JOBS,"Jobs",482,62,88,34);
        addButton(hwnd,ID_NAV_CONFIG,"Config",580,62,96,34);
        addGroup(hwnd,"Backup",18,118,996,168,&g_backupView);
        addLabel(hwnd,"Origem",42,156,80,22,&g_backupView);
        g_backupSource=addEdit(hwnd,ID_BACKUP_SOURCE,124,154,650,26,0,&g_backupView);
        addButton(hwnd,ID_PICK_BACKUP,"Selecionar",790,153,96,30,&g_backupView);
        addButton(hwnd,ID_BACKUP,"Executar backup",124,206,142,34,&g_backupView);
        addLabel(hwnd,"Use uma pasta de origem e execute o backup manual para o storage atual.",286,214,560,22,&g_backupView);
        addGroup(hwnd,"Restore",18,118,996,198,&g_restoreView);
        addLabel(hwnd,"Snapshot",42,156,80,22,&g_restoreView);
        g_restoreSnapshot=addEdit(hwnd,ID_RESTORE_SNAPSHOT,124,154,650,26,0,&g_restoreView);
        addLabel(hwnd,"Destino",42,198,80,22,&g_restoreView);
        g_target=addEdit(hwnd,ID_TARGET,124,196,650,26,0,&g_restoreView);
        addButton(hwnd,ID_PICK_RESTORE,"Selecionar",790,195,96,30,&g_restoreView);
        addButton(hwnd,ID_RESTORE,"Restaurar",124,246,104,34,&g_restoreView);
        addButton(hwnd,ID_VERIFY,"Verificar",238,246,104,34,&g_restoreView);
        addGroup(hwnd,"Snapshots",18,118,996,236,&g_snapshotsView);
        g_snaps=addList(hwnd,ID_SNAPSHOTS,42,154,730,144,&g_snapshotsView);
        addButton(hwnd,ID_REFRESH,"Atualizar",792,154,96,32,&g_snapshotsView);
        addButton(hwnd,ID_VERIFY,"Verificar",792,196,96,32,&g_snapshotsView);
        addButton(hwnd,ID_PRUNE,"Prune",792,238,96,32,&g_snapshotsView);
        addGroup(hwnd,"Arquivos",18,118,996,300,&g_filesView);
        addLabel(hwnd,"Snapshot",42,156,80,22,&g_filesView);
        g_filesSnapshot=addEdit(hwnd,ID_FILES_SNAPSHOT,124,154,650,26,0,&g_filesView);
        addButton(hwnd,ID_FILES_LOAD,"Carregar",792,153,96,30,&g_filesView);
        g_filesTree=addTree(hwnd,ID_FILE_TREE,42,200,948,190,&g_filesView);
        addGroup(hwnd,"Jobs",18,118,996,270,&g_jobsView);
        addLabel(hwnd,"Nome",42,156,54,22,&g_jobsView);g_jobName=addEdit(hwnd,ID_JOB_NAME,100,154,180,26,0,&g_jobsView);
        addLabel(hwnd,"Origem",304,156,64,22,&g_jobsView);g_source=addEdit(hwnd,ID_SOURCE,372,154,430,26,0,&g_jobsView);
        addLabel(hwnd,"Min",42,198,42,22,&g_jobsView);g_interval=addEdit(hwnd,ID_INTERVAL,100,196,70,26,0,&g_jobsView);setText(g_interval,"60");
        addLabel(hwnd,"Keep",198,198,48,22,&g_jobsView);g_keep=addEdit(hwnd,ID_KEEP,250,196,70,26,0,&g_jobsView);setText(g_keep,"10");
        addLabel(hwnd,"Poll",348,198,48,22,&g_jobsView);g_poll=addEdit(hwnd,ID_POLL,400,196,70,26,0,&g_jobsView);setText(g_poll,"30");
        g_jobs=addList(hwnd,ID_JOBS,42,240,626,96,&g_jobsView);
        addButton(hwnd,ID_ADD_JOB,"Salvar",688,240,86,32,&g_jobsView);
        addButton(hwnd,ID_REMOVE_JOB,"Remover",784,240,86,32,&g_jobsView);
        addButton(hwnd,ID_RUN_JOB,"Executar",880,240,86,32,&g_jobsView);
        addButton(hwnd,ID_START,"Iniciar",688,286,86,32,&g_jobsView);
        addButton(hwnd,ID_STOP,"Parar",784,286,86,32,&g_jobsView);
        addGroup(hwnd,"Configuracao",18,118,996,300,&g_configView);
        addLabel(hwnd,"Arquivo",42,156,70,22,&g_configView);g_config=addEdit(hwnd,ID_CONFIG,124,154,520,26,0,&g_configView);
        addButton(hwnd,ID_LOAD,"Carregar",662,153,88,30,&g_configView);addButton(hwnd,ID_SAVE,"Salvar",760,153,78,30,&g_configView);addButton(hwnd,ID_INIT_LOCAL,"Local",848,153,70,30,&g_configView);addButton(hwnd,ID_INIT_S3,"MinIO",926,153,70,30,&g_configView);
        addLabel(hwnd,"DB",42,202,42,22,&g_configView);g_db=addEdit(hwnd,ID_DB,124,200,250,26,0,&g_configView);
        addLabel(hwnd,"Tipo",400,202,46,22,&g_configView);g_type=addCombo(hwnd,ID_TYPE,448,200,92,120,&g_configView);
        addLabel(hwnd,"Endpoint",570,202,78,22,&g_configView);g_endpoint=addEdit(hwnd,ID_ENDPOINT,652,200,342,26,0,&g_configView);
        addLabel(hwnd,"Bucket",42,244,70,22,&g_configView);g_bucket=addEdit(hwnd,ID_BUCKET,124,242,190,26,0,&g_configView);
        addLabel(hwnd,"Access",338,244,64,22,&g_configView);g_access=addEdit(hwnd,ID_ACCESS,406,242,170,26,0,&g_configView);
        addLabel(hwnd,"Secret",600,244,64,22,&g_configView);g_secret=addEdit(hwnd,ID_SECRET,668,242,160,26,ES_PASSWORD,&g_configView);
        addLabel(hwnd,"Region",850,244,60,22,&g_configView);g_region=addEdit(hwnd,ID_REGION,914,242,80,26,0,&g_configView);
        addLabel(hwnd,"Repo",42,286,60,22,&g_configView);g_local=addEdit(hwnd,ID_LOCAL,124,284,250,26,0,&g_configView);
        addLabel(hwnd,"Prefix",400,286,58,22,&g_configView);g_prefix=addEdit(hwnd,ID_PREFIX,462,284,260,26,0,&g_configView);
        g_enc=addCheck(hwnd,ID_ENC,"Criptografia",742,284,116,26,&g_configView);
        addLabel(hwnd,"Key",870,286,34,22,&g_configView);g_key=addEdit(hwnd,ID_KEY,914,284,80,26,0,&g_configView);
        addGroup(hwnd,"Eventos",18,430,996,142);
        g_log=CreateWindowExA(WS_EX_CLIENTEDGE,"EDIT","",WS_CHILD|WS_VISIBLE|WS_VSCROLL|ES_MULTILINE|ES_READONLY|ES_AUTOVSCROLL,42,460,948,82,hwnd,menuId(ID_LOG),GetModuleHandleA(nullptr),nullptr);
        SendMessageA(g_log,WM_SETFONT,reinterpret_cast<WPARAM>(g_font),TRUE);
        try{g_cfg=Config::load(g_configPath);appendLog("config carregada");}catch(const std::exception& e){g_cfg=Config{};appendLog(std::string("config: ")+e.what());}
        fillUi();
        showModule(ID_NAV_BACKUP);
        return 0;
    }
    case WM_CTLCOLORSTATIC:{
        HDC dc=reinterpret_cast<HDC>(wp);
        SetBkMode(dc,TRANSPARENT);
        SetTextColor(dc,reinterpret_cast<HWND>(lp)==g_status?RGB(0,127,255):RGB(25,35,45));
        return reinterpret_cast<LRESULT>(g_bgBrush);
    }
    case WM_COMMAND:
        try{command(LOWORD(wp));}catch(const std::exception& e){appendLog(std::string("erro: ")+e.what());}
        return 0;
    case WM_LOG:{
        std::unique_ptr<std::string> s(reinterpret_cast<std::string*>(lp));
        appendLog(*s);
        return 0;
    }
    case WM_REFRESH:
        try{g_cfg=Config::load(g_configPath);fillUi();}catch(const std::exception& e){appendLog(std::string("refresh: ")+e.what());}
        return 0;
    case WM_DESTROY:
        stopAgent();
        if(g_font)DeleteObject(g_font);
        if(g_titleFont)DeleteObject(g_titleFont);
        if(g_bgBrush)DeleteObject(g_bgBrush);
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcA(hwnd,msg,wp,lp);
    }
}

int APIENTRY WinMain(HINSTANCE inst,HINSTANCE,LPSTR,int show){
    int argc=0;
    LPWSTR* argv=CommandLineToArgvW(GetCommandLineW(),&argc);
    if(argv&&argc>1)g_configPath=wideToUtf8(argv[1]);
    if(argv)LocalFree(argv);
    WNDCLASSA wc{};
    wc.lpfnWndProc=wndProc;
    wc.hInstance=inst;
    wc.lpszClassName="KeeplyGuiWindow";
    wc.hCursor=LoadCursor(nullptr,IDC_ARROW);
    wc.hbrBackground=CreateSolidBrush(RGB(247,249,252));
    RegisterClassA(&wc);
    HWND hwnd=CreateWindowA("KeeplyGuiWindow","Keeply",WS_OVERLAPPED|WS_CAPTION|WS_SYSMENU|WS_MINIMIZEBOX,100,100,1046,640,nullptr,nullptr,inst,nullptr);
    ShowWindow(hwnd,show);
    UpdateWindow(hwnd);
    MSG msg{};
    while(GetMessageA(&msg,nullptr,0,0)>0){
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }
    return static_cast<int>(msg.wParam);
}
