#include <minecraft-file.hpp>
#include <iostream>

using namespace std;
using namespace mcfile;
using namespace mcfile::je;
namespace fs = std::filesystem;

namespace {

bool SquashRegionFile(int rx, int rz, fs::path filePath, fs::path tmp, fs::path squashed) {
    auto beforeSize = fs::file_size(filePath);
    if (beforeSize == 0) {
        return true;
    }

    fs::path regionFile = tmp / filePath.filename();

    string name = "s." + to_string(rx) + "." + to_string(rz) + ".smca";
    fs::path squashedFile = tmp / name;

    error_code ec;
    fs::path targetFile = squashed / name;
    if (fs::is_regular_file(targetFile, ec)) {
        auto original = fs::last_write_time(filePath);
        auto target = fs::last_write_time(targetFile);
        auto afterSize = fs::file_size(targetFile);
        if (original <= target) {
            int64_t diff = (int64_t)afterSize - (int64_t)beforeSize;
            cout << name << ":\t";
            cout << (beforeSize / 1024.f) << " KiB -> ";
            cout << (afterSize / 1024.f) << " KiB (" << (diff < 0 ? "" : "+") << (diff * 100.0f / beforeSize) << "%, newer than original, skip)" << endl;
            return true;
        }
    }
    
    fs::copy_file(filePath, regionFile);
    auto region = Region::MakeRegion(regionFile);
    if (!region) {
        cerr << "Error: failed loading region from " << regionFile << endl;
        fs::remove(regionFile);
        return false;
    }

    FILE* file = File::Open(squashedFile, File::Mode::Write);
    if (!file) {
        cerr << "Error: cannot open file: " << (squashed / name) << endl;
        return false;
    }
    uint32_t pos = sizeof(uint32_t) * (32 * 32 + 1);
    if (!File::Fseek(file, pos, SEEK_SET)) {
        cerr << "Error: fseek failed: " << (squashed / name) << endl;
        fclose(file);
        fs::remove(squashedFile);
        return false;
    }
    vector<uint32_t> index;
    index.push_back(pos);
    for (int cz = region->minChunkZ(); cz <= region->maxChunkZ(); cz++) {
        for (int cx = region->minChunkX(); cx <= region->maxChunkX(); cx++) {
            string chunkFileName = Region::GetDefaultCompressedChunkNbtFileName(cx, cz);
            fs::path chunkFile = tmp / chunkFileName;
            if (region->exportToCompressedNbt(cx, cz, chunkFile)) {
                auto size = fs::file_size(chunkFile);
                pos += size;
                index.push_back(pos);
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
                index.push_back(pos);
            }
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
    
    fs::remove(squashed / name);
    fs::copy_file(squashedFile, squashed / name);
    fs::remove(squashedFile);
    
    return true;
}

bool SquashRegionFiles(fs::path worldDirectory) {
    if (!fs::exists(worldDirectory)) {
        return false;
    }
    if (!fs::is_directory(worldDirectory)) {
        return false;
    }
    World w(worldDirectory);
    auto squashed = worldDirectory / "squashed_region";
    fs::create_directories(squashed);
    fs::path temp_directory = fs::temp_directory_path();
    if (fs::exists(fs::path("/tmp")) && fs::is_directory(fs::path("/tmp"))) {
        temp_directory = fs::path("/tmp");
    }
    auto tmp = File::CreateTempDir(temp_directory);
    if (!tmp) {
        cerr << "Error: cannot create temporary directory" << endl;
        return false;
    }
    
    bool ok = w.eachRegions([squashed, tmp](int rx, int rz, fs::path file) {
        return SquashRegionFile(rx, rz, file, *tmp, squashed);
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
    if (!fs::exists(root)) {
        cerr << "Error: root directory does not exists: " << root << endl;
        return 1;
    }
    if (!fs::is_directory(root)) {
        cerr << "Error: " << root << " is not a directory" << endl;
        return 1;
    }
    SquashRegionFiles(root / "world");
    SquashRegionFiles(root / "world_nether" / "DIM-1");
    SquashRegionFiles(root / "world_the_end" / "DIM1");
    return 0;
}
