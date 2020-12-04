#include "minecraft-file.hpp"
#include <string>
#include <iostream>
#include <set>

using namespace std;
using namespace mcfile;
namespace fs = mcfile::detail::filesystem;

static void PrintError(string const& message) {
    cerr << "core -w [world directory] -x [min block x] -X [max block x] -y [min block y] -Y [max block y] -z [min block z] -Z [max block z]" << endl;
    cout << "{" << endl;
    cout << "  status: \"" << message << "\"" << endl;
    cout << "}" << endl;
}

static bool kDebug = false;

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

static string NewLine() {
    return kDebug ? "\n" : "";
}

static string NamespacedId(string const& name) {
    if (name.find("minecraft:") == 0) {
        return name.substr(10);
    } else {
        return ":" + name;
    }
}

static string BlockName(shared_ptr<Block const> const& block) {
    if (!block) {
        return "air";
    }
    return NamespacedId(block->toString());
}

template<class T, class V>
static void PrintVectorContent(vector<T> const& v, int indent, function<V(T const& v)> convert) {
    auto nl = NewLine();
    auto it = v.begin();
    while (true) {
        cout << Indent(indent) << convert(*it);
        it++;
        if (it == v.end()) {
            cout << nl;
            break;
        } else {
            cout << "," << nl;
        }
    }
}

static void PrintPaletteAndIndices(vector<string> const& list, int indent, string const& nl) {
    map<string, int> usage;
    for_each(list.begin(), list.end(), [&usage](string const& b) {
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

    cout << Indent(indent) << "palette:[" << nl;
    PrintVectorContent<string, string>(palette, indent + 1, [](string const& v) {
        return "\"" + v + "\"";
    });
    cout << Indent(indent) << "]," << nl;
    cout << Indent(indent) << "indices:[" << nl;
    PrintVectorContent<string, int>(list, indent + 1, [&palette](string const& b) {
        auto found = find(palette.begin(), palette.end(), b);
        return distance(palette.begin(), found);
    });
    cout << Indent(indent) << "]" << nl;
}

int main(int argc, char *argv[]) {
    string input;
    int minBx = INT_MAX;
    int maxBx = INT_MIN;
    int minBy = INT_MAX;
    int maxBy = INT_MIN;
    int minBz = INT_MAX;
    int maxBz = INT_MIN;

    int opt;
    opterr = 0;
    while ((opt = getopt(argc, argv, "w:x:X:y:Y:z:Z:d")) != -1) {
        switch (opt) {
            case 'w':
                input = optarg;
                break;
            case 'd': {
                kDebug = true;
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
    if (input.empty()) {
        PrintError("invalid world");
        return 1;
    }

    string const nl = NewLine();

    int const minRx = Coordinate::RegionFromBlock(minBx);
    int const maxRx = Coordinate::RegionFromBlock(maxBx);
    int const minRz = Coordinate::RegionFromBlock(minBz);
    int const maxRz = Coordinate::RegionFromBlock(maxBz);
    int const minCx = Coordinate::ChunkFromBlock(minBx);
    int const maxCx = Coordinate::ChunkFromBlock(maxBx);
    int const minCz = Coordinate::ChunkFromBlock(minBz);
    int const maxCz = Coordinate::ChunkFromBlock(maxBz);
    int const dBx = maxBx - minBx + 1;
    int const dBy = maxBy - minBy + 1;
    int const dBz = maxBz - minBz + 1;
    int const volume = dBx * dBy * dBz;
    vector<string> blocks(volume);
    vector<string> biomes(volume);

    if (fs::exists(fs::path(input) / "chunk")) {
        int count = 0;
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                auto file = fs::path(input) / "chunk" / Region::GetDefaultCompressedChunkNbtFileName(cx, cz);
                auto const& chunk = Chunk::LoadFromCompressedChunkNbtFile(file.string(), cx, cz);
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
                            auto const biome = chunk->biomeAt(x, y, z);

                            int const idx = (x - minBx) + (z - minBz) * dBx + (y - minBy) * (dBx * dBz);
                            blocks[idx] = BlockName(block);
                            biomes[idx] = NamespacedId(mcfile::biomes::Name(biome, chunk->fDataVersion));
                            count++;
                        }
                    }
                }
            }
        }
    } else {
        World world(input);
        int count = 0;
        for (int rz = minRz; rz <= maxRz; rz++) {
            for (int rx = minRx; rx <= maxRx; rx++) {
                auto const& region = world.region(rx, rz);
                int const minCxInRegion = (std::max)(region->minChunkX(), minCx);
                int const maxCxInRegion = (std::min)(region->maxChunkX(), maxCx);
                int const minCzInRegion = (std::max)(region->minChunkZ(), minCz);
                int const maxCzInRegion = (std::min)(region->maxChunkZ(), maxCz);
                for (int cx = minCxInRegion; cx <= maxCxInRegion; cx++) {
                    for (int cz = minCzInRegion; cz <= maxCzInRegion; cz++) {
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
                                    auto const biome = chunk->biomeAt(x, y, z);

                                    int const idx = (x - minBx) + (z - minBz) * dBx + (y - minBy) * (dBx * dBz);
                                    blocks[idx] = BlockName(block);
                                    biomes[idx] = NamespacedId(mcfile::biomes::Name(biome, chunk->fDataVersion));
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    cout << "{" << nl;
    cout << Indent(1) << "status:\"ok\"," << nl;
    cout << Indent(1) << "block:{" << nl;
    PrintPaletteAndIndices(blocks, 2, nl);
    cout << Indent(1) << "}," << nl;
    cout << Indent(1) << "biome:{" << nl;
    PrintPaletteAndIndices(biomes, 2, nl);
    cout << Indent(1) << "}" << nl;
    cout << "}" << nl;
}
