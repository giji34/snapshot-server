#include <minecraft-file.hpp>
#include <iostream>
#include <sstream>
#include <fstream>
#include <set>
#include "hwm/task/task_queue.hpp"
#include <unistd.h>
#include <filesystem>

using namespace std;
using namespace mcfile;
namespace fs = mcfile::detail::filesystem;

static void PrintDescription() {
    cerr << "validate-preparation" << endl;
    cerr << "SYNOPSYS" << endl;
    cerr << "    validate-preparation -f db_directory_path -w world_directory -d dimension -v minecraft_version" << endl;
    cerr << "DIMENSION" << endl;
    cerr << "    0:  Overworld" << endl;
    cerr << "    -1: The Nether" << endl;
    cerr << "    1:  The End" << endl;
    cerr << "MINECRAFT VERSION" << endl;
    cerr << "    1.13 etc." << endl;
}

static int CountRegionFiles(string worldDir) {
    int numRegions = 0;
    cout << "couting the number of *.mca files..." << endl;
    for (auto const& e : fs::directory_iterator(fs::path(worldDir) / "region")) {
        int x, z;
        if (sscanf(e.path().filename().c_str(), "r.%d.%d.mca", &x, &z) == 2) {
            numRegions++;
        }
    }
    cout << numRegions << " regions found" << endl;
    return numRegions;
}

static vector<string> Split(string const&s, char delim) {
    vector<string> elems;
    stringstream ss(s);
    string item;
    while (getline(ss, item, delim)) {
        if (!item.empty()) {
            elems.push_back(item);
        }
    }
    return elems;
}

int main(int argc, char *argv[]) {
    string dbDir;
    string worldDir;
    int dimension = 0;
    string version;

    int opt;
    opterr = 0;
    while ((opt = getopt(argc, argv, "f:w:d:v:c")) != -1) {
        switch (opt) {
            case 'f':
                dbDir = optarg;
                break;
            case 'w':
                worldDir = optarg;
                break;
            case 'd':
                if (sscanf(optarg, "%d", &dimension) != 1) {
                    PrintDescription();
                    return -1;
                }
                break;
            case 'v':
                version = optarg;
                break;
            default:
                PrintDescription();
                return -1;
        }
    }
    
    if (argc < 5) {
        PrintDescription();
        return -1;
    }
    cout << "db:        " << dbDir << endl;
    cout << "world:     " << worldDir << endl;
    cout << "dimension: " << dimension << endl;
    cout << "version:   " << version << endl;

    fs::path const rootDir = fs::path(dbDir) / version / to_string(dimension);
    
    if (!fs::exists(rootDir)) {
        fs::create_directories(rootDir);
    }
    if (!fs::is_directory(rootDir)) {
        cerr << "\"" << rootDir << "\" がディレクトリじゃない" << endl;
        exit(1);
    }

    set<pair<int, int>> existingIndex;
    fs::path const indexFile = rootDir / "index.txt";
    {
        cout << "reading " << indexFile << " ..." << endl;
        ifstream fs(indexFile.c_str());
        string line;
        while (getline(fs, line)) {
            vector<string> tokens = Split(line, '\t');
            int x;
            if (sscanf(tokens[0].c_str(), "%d", &x) != 1) {
                return 1;
            }
            int z;
            if (sscanf(tokens[1].c_str(), "%d", &z) != 1) {
                return 1;
            }
            existingIndex.insert(make_pair(x, z));
        }
    }
    
    World world(worldDir);
    int const numRegions = CountRegionFiles(worldDir);

    hwm::task_queue q(thread::hardware_concurrency());
    vector<future<set<pair<int, int>>>> futures;
    mutex logMutex;
    int finishedRegions = 0;
    size_t lastLineLength = 0;
    
    cout << "queueing tasks..." << endl;
    world.eachRegions([numRegions, &futures, &q, &logMutex, &finishedRegions, &existingIndex, &lastLineLength](shared_ptr<Region> const& region) {
        futures.emplace_back(q.enqueue([numRegions, &logMutex, &finishedRegions, &existingIndex, &lastLineLength](shared_ptr<Region> const& region) {
            set<pair<int, int>> result;
            for (int lcx = 0; lcx < 32; lcx++) {
                int const chunkX = region->fX * 32 + lcx;
                for (int lcz = 0; lcz < 32; lcz++) {
                    int const chunkZ = region->fZ * 32 + lcz;
                    pair<int, int> p = make_pair(chunkX, chunkZ);
                    if (existingIndex.find(p) != existingIndex.end()) {
                        continue;
                    }
                    auto const& chunk = region->chunkAt(chunkX, chunkZ);
                    if (!chunk) {
                        continue;
                    }
                    if (chunk->status() == Chunk::Status::FULL) {
                        result.insert(p);
                    }
                }
            }

            lock_guard<mutex> lk(logMutex);
            finishedRegions++;
            string line = to_string(finishedRegions) + "/" + to_string(numRegions) + " " + to_string(float(finishedRegions * 100.0f / numRegions)) + "%";
            cout << "\r" << line;
            if (line.length() < lastLineLength) {
                cout << string(lastLineLength - line.length(), ' ');
            }
            lastLineLength = line.length();
            return result;
        }, region));
        return true;
    });

    ofstream fs(indexFile.c_str());
    for (auto it = existingIndex.begin(); it != existingIndex.end(); it++) {
        fs << it->first << "\t" << it->second << endl;
    }
    int added = 0;
    for (auto& f : futures) {
        set<pair<int, int>> result = f.get();
        for (auto it = result.begin(); it != result.end(); it++) {
            fs << it->first << "\t" << it->second << endl;
            added++;
        }
        fs.flush();
    }
    fs.close();

    cout << endl << added << " chunks validated" << endl;
    cout << "finished" << endl;
}
