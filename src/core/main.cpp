#include "minecraft-file.hpp"
#include <string>
#include <iostream>
#include <set>

using namespace std;
using namespace mcfile;
namespace fs = mcfile::detail::filesystem;

static void PrintError(string const& message) {
    cerr << "core -w [world directory] -x [min block x] -X [max block x] -y [min block y] -Y [max block y] -z [min block z] -Z [max block z] -d [dimension; o:overworld, n:nether, e:the end]" << endl;
    cout << "{" << endl;
    cout << "  status: \"" << message << "\"" << endl;
    cout << "}" << endl;
}

static bool const kDebug = true;

static string Indent(int n) {
    if (kDebug) {
        string s = "  ";
        if (n == 1) {
            return s;
        } else if (n == 2) {
            return s + s;
        } else {
            ostringstream ss;
            for (int i = 0; i < n; i++) {
                ss << s;
            }
            return ss.str();
        }
    } else {
        return "";
    }
}

int main(int argc, char *argv[]) {
    string const nl = kDebug ? "\n" : "";
    
    string input;
    int dimension = 100;
    int minBx = INT_MAX;
    int maxBx = INT_MIN;
    int minBy = INT_MAX;
    int maxBy = INT_MIN;
    int minBz = INT_MAX;
    int maxBz = INT_MIN;

    int opt;
    opterr = 0;
    while ((opt = getopt(argc, argv, "w:x:X:y:Y:z:Z:d:")) != -1) {
        switch (opt) {
            case 'w':
                input = optarg;
                break;
            case 'd': {
                string d(optarg);
                if (d == "o") {
                    dimension = 0;
                } else if (d == "n") {
                    dimension = -1;
                } else if (d == "e") {
                    dimension = 1;
                } else {
                    PrintError("invalid dimension spec: " + d);
                    return 1;
                }
                break;
            }
            case 'x':
                if (sscanf(optarg, "%d", &minBx) != 1) {
                    PrintError("invalid x: " + string(optarg));
                    return 1;
                }
                break;
            case 'X':
                if (sscanf(optarg, "%d", &maxBx) != 1) {
                    PrintError("invalid X: " + string(optarg));
                    return 1;
                }
                break;
            case 'y':
                if (sscanf(optarg, "%d", &minBy) != 1) {
                    PrintError("invalid y: " + string(optarg));
                    return 1;
                }
                break;
            case 'Y':
                if (sscanf(optarg, "%d", &maxBy) != 1) {
                    PrintError("invalid Y: " + string(optarg));
                    return 1;
                }
                break;
            case 'z':
                if (sscanf(optarg, "%d", &minBz) != 1) {
                    PrintError("invalid z: " + string(optarg));
                    return 1;
                }
                break;
            case 'Z':
                if (sscanf(optarg, "%d", &maxBz) != 1) {
                    PrintError("invalid Z: " + string(optarg));
                    return 1;
                }
                break;
            default:
                PrintError("invalid option");
                return 1;
        }
    }
    if (minBx > maxBx || minBy > maxBy || minBz > maxBz) {
        PrintError("invalid block range");
        return 1;
    }
    if (dimension < -1 || 1 < dimension) {
        PrintError("invalid dimension");
        return 1;
    }
    if (input.empty()) {
        PrintError("invalid world");
        return 1;
    }

    int const minRx = Coordinate::RegionFromBlock(minBx);
    int const maxRx = Coordinate::RegionFromBlock(maxBx);
    int const minRz = Coordinate::RegionFromBlock(minBz);
    int const maxRz = Coordinate::RegionFromBlock(maxBz);
    int const dBx = maxBx - minBx + 1;
    int const dBy = maxBy - minBy + 1;
    int const dBz = maxBz - minBz + 1;
    int const volume = dBx * dBy * dBz;
    vector<string> blocks(volume);

    if (fs::exists(fs::path(input).append("chunk"))) {
        PrintError("not implemented yet");
        return 1;
    } else {
        World world(input);
        int count = 0;
        for (int rz = minRz; rz <= maxRz; rz++) {
            for (int rx = minRx; rx <= maxRx; rx++) {
                auto const& region = world.region(rx, rz);
                int const minCx = (std::max)(region->minChunkX(), Coordinate::ChunkFromBlock(minBx));
                int const maxCx = (std::min)(region->maxChunkX(), Coordinate::ChunkFromBlock(maxBx));
                int const minCz = (std::max)(region->minChunkZ(), Coordinate::ChunkFromBlock(minBz));
                int const maxCz = (std::min)(region->maxChunkZ(), Coordinate::ChunkFromBlock(maxBz));
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cz = minCz; cz <= maxCz; cz++) {
                        auto const& chunk = region->chunkAt(cx, cz);
                        if (!chunk) {
                            PrintError("chunk [" + to_string(cx) + ", " + to_string(cz) + "] not saved yet");
                            return 1;
                        }
                        int const minX = (std::max)(chunk->minBlockX(), minBx);
                        int const maxX = (std::min)(chunk->maxBlockX(), maxBx);
                        int const minZ = (std::max)(chunk->minBlockZ(), minBz);
                        int const maxZ = (std::min)(chunk->maxBlockZ(), maxBz);
                        for (int y = minBy; y <= maxBy; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                for (int x = minX; x <= maxX; x++) {
                                    auto const& block = chunk->blockAt(x, y, z);
                                    int const idx = (x - minBx) + (z - minBz) * dBx + (y - minBy) * (dBx * dBz);
                                    if (block) {
                                        blocks[idx] = block->toString();
                                    } else {
                                        blocks[idx] = "minecraft:air";
                                    }
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    map<string, int> usage;
    for_each(blocks.begin(), blocks.end(), [&usage](string const& b) {
        usage[b] += 1;
    });
    vector<string> palette;
    palette.reserve(usage.size());
    for_each(usage.begin(), usage.end(), [&palette](auto it) {
        palette.push_back(it.first);
    });
    sort(palette.begin(), palette.end(), [&usage](string const& a, string const& b) {
        return usage[a] > usage[b];
    });
    cout << "{" << nl;
    cout << Indent(1) << "status:\"ok\"," << nl;
    cout << Indent(1) << "palette:[" << nl;
    {
        auto it = palette.begin();
        while (true) {
            cout << Indent(2) << "\"" << *it << "\"";
            it++;
            if (it == palette.end()) {
                cout << nl;
                break;
            } else {
                cout << "," << nl;
            }
        }
    }
    cout << Indent(1) << "]," << nl;
    cout << Indent(1) << "blocks:[" << nl;
    {
        auto it = blocks.begin();
        while (true) {
            auto found = find(palette.begin(), palette.end(), *it);
            int const idx = distance(palette.begin(), found);
            cout << Indent(2) << idx;
            it++;
            if (it == blocks.end()) {
                cout << nl;
                break;
            } else {
                cout << "," << nl;
            }
        }
    }
    cout << Indent(1) << "]" << nl;
    cout << "}" << nl;
}
