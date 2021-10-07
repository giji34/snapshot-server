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
    auto tmp = File::CreateTempDir(std::filesystem::temp_directory_path());
    if (!tmp) {
        cerr << "Error: cannot create temporary directory" << endl;
        return false;
    }
    
    bool ok = w.eachRegions([worldDirectory, squashed, tmp](shared_ptr<Region> const& r) {
        auto beforeSize = fs::file_size(r->fFilePath);
        if (beforeSize == 0) {
            return true;
        }

        fs::path regionFile = *tmp / r->fFilePath.filename();
        fs::copy_file(r->fFilePath, regionFile);
        auto region = Region::MakeRegion(regionFile);
        if (!region) {
            cerr << "Error: failed loading region from " << regionFile << endl;
            fs::remove(regionFile);
            return false;
        }

        string name = "s." + to_string(region->fX) + "." + to_string(region->fZ) + ".mca";
        fs::path squashedFile = *tmp / name;
        FILE* file = File::Open(squashedFile, File::Mode::Write);
        if (!file) {
            cerr << "Error: cannot open file: " << (squashed / name) << endl;
            return false;
        }
        if (!File::Fseek(file, sizeof(uint32_t) * 32 * 32 * 2, SEEK_SET)) {
            cerr << "Error: fseek failed: " << (squashed / name) << endl;
            fclose(file);
            fs::remove(squashedFile);
            return false;
        }
        vector<uint32_t> index(32 * 32 * 2);
        int count = 0;
        for (int cz = region->minChunkZ(); cz <= region->maxChunkZ(); cz++) {
            for (int cx = region->minChunkX(); cx <= region->maxChunkX(); cx++) {
                string chunkFileName = Region::GetDefaultCompressedChunkNbtFileName(cx, cz);
                fs::path chunkFile = *tmp / chunkFileName;
                if (region->exportToCompressedNbt(cx, cz, chunkFile)) {
                    auto pos = File::Ftell(file);
                    if (!pos) {
                        cerr << "Error: ftell failed:" << chunkFile << endl;
                        fclose(file);
                        fs::remove(squashedFile);
                        return false;
                    }
                    auto size = fs::file_size(chunkFile);
                    index[count] = *pos;
                    index[count + 1] = size;
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
                    index[count + 1] = 0;
                }
                count += 2;
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
        
        fs::remove(regionFile);
        fs::remove(region->fFilePath);
        fs::rename(squashedFile, squashed / name);
        
        return true;
    });
    
    fs::remove_all(*tmp);
    
    return ok;
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
