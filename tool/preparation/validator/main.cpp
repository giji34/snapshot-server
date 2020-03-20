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
namespace fs = std::filesystem;

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
    for (auto const& e : fs::directory_iterator(fs::path(worldDir) / "region")) {
        int x, z;
        if (sscanf(e.path().filename().c_str(), "r.%d.%d.mca", &x, &z) == 2) {
            numRegions++;
        }
    }
    return numRegions;
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
    cout << "rootDir=" << rootDir << endl;
    
    if (!fs::exists(rootDir)) {
        fs::create_directories(rootDir);
    }
    if (!fs::is_directory(rootDir)) {
        cerr << "\"" << rootDir << "\" がディレクトリじゃない" << endl;
        exit(1);
    }

    World world(worldDir);
    int const numRegions = CountRegionFiles(worldDir);

    hwm::task_queue q(thread::hardware_concurrency());
    vector<future<void>> futures;
    mutex logMutex;
    int finishedRegions = 0;
    
    world.eachRegions([=, &futures, &q, &logMutex, &finishedRegions](shared_ptr<Region> const& region) {
        futures.emplace_back(q.enqueue([=, &logMutex, &finishedRegions](shared_ptr<Region> const& region) {
            for (int lcx = 0; lcx < 32; lcx++) {
                int const chunkX = region->fX * 32 + lcx;
                for (int lcz = 0; lcz < 32; lcz++) {
                    int const chunkZ = region->fZ * 32 + lcz;
                    fs::path file = rootDir / ("c." + to_string(chunkX) + "." + to_string(chunkZ) + ".idx");
                    if (fs::exists(file)) {
                        continue;
                    }
                    auto const& chunk = region->chunkAt(chunkX, chunkZ);
                    if (chunk) {
                        FILE *fp = fopen(file.c_str(), "w+b");
                        fclose(fp);
                    }
                }
            }

            lock_guard<mutex> lk(logMutex);
            finishedRegions++;
            cout << finishedRegions << "/" << numRegions << "\t" << float(finishedRegions * 100.0f / numRegions) << "%" << endl;
        }, region));
    });
    
    for (auto& f : futures) {
        f.get();
    }
}
