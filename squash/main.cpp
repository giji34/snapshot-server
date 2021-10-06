#include <minecraft-file.hpp>
#include <iostream>

using namespace std;
using namespace mcfile;
using namespace mcfile::je;
namespace fs = std::filesystem;

namespace {

bool SquashRegionFiles(fs::path worldDirectory) {
    World w(worldDirectory);
    auto squashed = worldDirectory / "squashed_region";
    fs::create_directories(squashed);
    bool ok = w.eachRegions([worldDirectory, squashed](shared_ptr<Region> const& region) {
        auto beforeSize = fs::file_size(region->fFilePath);
        if (beforeSize == 0) {
            return true;
        }
        
        string name = "s." + to_string(region->fX) + "." + to_string(region->fZ) + ".mca";
        fs::path squashedFile = squashed / name;
        FILE* file = File::Open(squashedFile, File::Mode::Write);
        if (!file) {
            cerr << "Error: cannot open file: " << (squashed / name) << endl;
            return false;
        }
        if (!File::Fseek(file, sizeof(uint32_t) * 32 * 32, SEEK_SET)) {
            cerr << "Error: fseek failed: " << (squashed / name) << endl;
            fclose(file);
            fs::remove(squashedFile);
            return false;
        }
        vector<uint32_t> index(32 * 32);
        int count = 0;
        uint64_t totalSize = 0;
        for (int cz = region->minChunkZ(); cz <= region->maxChunkZ(); cz++) {
            for (int cx = region->minChunkX(); cx <= region->maxChunkX(); cx++) {
                string chunkFileName = Region::GetDefaultCompressedChunkNbtFileName(cx, cz);
                fs::path chunkFile = squashed / chunkFileName;
                if (region->exportToCompressedNbt(cx, cz, chunkFile)) {
                    auto pos = File::Ftell(file);
                    if (!pos) {
                        cerr << "Error: ftell failed:" << chunkFile << endl;
                        fclose(file);
                        fs::remove(squashedFile);
                        return false;
                    }
                    index[count] = *pos;
                    auto size = fs::file_size(chunkFile);
                    totalSize += size;
                    FILE *in = File::Open(chunkFile, File::Mode::Read);
                    if (!in) {
                        cerr << "Error: cannot open file: " << chunkFile << endl;
                        fclose(file);
                        fs::remove(squashedFile);
                        return false;
                    }
                    if (!File::Copy(in, file, size)) {
                        cerr << "Error: failed copying contents of " << chunkFile << " to " << squashedFile << endl;
                        fclose(in);
                        fclose(file);
                        fs::remove(chunkFile);
                        fs::remove(squashedFile);
                        return false;
                    }
                    fclose(in);
                    fs::remove(chunkFile);
                } else {
                    index[count] = 0;
                }
                count++;
            }
        }
        
        if (!File::Fseek(file, 0, SEEK_SET)) {
            cerr << "Error: cannot seek to head of " << squashedFile << endl;
            fclose(file);
            fs::remove(squashedFile);
            return false;
        }
        
        if (!File::Fwrite(index.data(), sizeof(uint32_t), index.size(), file)) {
            cerr << "Error: cannot write index: " << squashedFile << endl;
            fclose(file);
            fs::remove(squashedFile);
            return false;
        }
        
        fclose(file);

        auto afterSize = fs::file_size(squashedFile);
        int64_t diff = (int64_t)afterSize - (int64_t)beforeSize;
        
        cout << name << ":\t";
        cout << (beforeSize / 1024.f) << " KiB -> ";
        cout << (afterSize / 1024.f) << " KiB (" << (diff < 0 ? "" : "+") << (diff * 100.0f / beforeSize) << "%)" << endl;
        
        fs::remove(region->fFilePath);
        
        return true;
    });
    return true;
}

void PrintUsage() {
    cerr << "squash <SERVER_DIRECTORY>" << endl;
}

}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        PrintUsage();
        return 1;
    }
    fs::path root = argv[1];
    SquashRegionFiles(root / "world");
    SquashRegionFiles(root / "world_nether" / "DIM-1");
    SquashRegionFiles(root / "world_the_end" / "DIM1");
    return 0;
}
