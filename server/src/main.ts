import * as express from "express";
import { Server } from "http";
import * as caporal from "caporal";
import * as path from "path";
import * as child_process from "child_process";
import * as sprintf from "sprintf-js";
import { mkdirSync, mkdtempSync, rmdir } from "fs";
import * as fs from "fs";

function getWild(wildDirectory: string, core: string) {
  return (req, res) => {
    try {
      const {
        version,
        dimension,
        minX,
        maxX,
        minY,
        maxY,
        minZ,
        maxZ,
      } = req.query;
      const dim = parseInt(dimension, 10);
      let world: string;
      if (dim === -1) {
        world = path.join(wildDirectory, version, "world_nether", "DIM-1");
      } else if (dim === 1) {
        world = path.join(wildDirectory, version, "world_the_end", "DIM1");
      } else {
        world = path.join(wildDirectory, version, "world");
      }
      const p = child_process.spawn(core, [
        "-w",
        world,
        "-x",
        minX,
        "-X",
        maxX,
        "-y",
        minY,
        "-Y",
        maxY,
        "-z",
        minZ,
        "-Z",
        maxZ,
      ]);
      p.stdout.pipe(res);
      p.stderr.pipe(process.stderr);
    } catch (e) {
      res.status(500).send(`{status:"fatal error"}`);
    }
  };
}

async function checkoutByHash(
  directory: string,
  stdout: string,
  params: {
    historyDirectory: string;
    minX: number;
    maxX: number;
    minZ: number;
    maxZ: number;
  }
) {
  const { historyDirectory, minX, maxX, minZ, maxZ } = params;
  const minCx = minX >> 4;
  const maxCx = maxX >> 4;
  const minCz = minZ >> 4;
  const maxCz = maxZ >> 4;
  const chunkDir = path.join(directory, "chunk");
  const promises: Promise<void>[] = stdout.split("\n").map((line) => {
    return new Promise((resolve, reject) => {
      if (line.length < 1) {
        resolve();
        return;
      }
      const [blobHash, filePath] = line.split(" ");
      const name = path.basename(filePath); // c.-113.198.nbt.z
      const tokens = name.split(".");
      const cx = parseInt(tokens[1]);
      const cz = parseInt(tokens[2]);
      if (cx < minCx || maxCx < cx || cz < minCz || maxCz < cz) {
        resolve();
        return;
      }
      const catFile = child_process.spawn("git", ["cat-file", "-p", blobHash], {
        cwd: historyDirectory,
      });
      const destination = path.join(chunkDir, name);
      fs.open(destination, "w", (err, fd) => {
        if (err) {
          fs.closeSync(fd);
          reject();
          return;
        }
        catFile.stdout.on("data", (data) => {
          fs.writeSync(fd, data);
        });
        catFile.on("close", () => {
          fs.closeSync(fd);
          resolve();
        });
      });
    });
  });
  await Promise.all(promises);
}

function sendByHash(
  req,
  res,
  params: {
    core: string;
    historyDirectory: string;
    hash: string;
    dimension: number;
    minX: number;
    maxX: number;
    minY: number;
    maxY: number;
    minZ: number;
    maxZ: number;
  }
) {
  const {
    core,
    hash,
    dimension,
    minX,
    maxX,
    minY,
    maxY,
    minZ,
    maxZ,
    historyDirectory,
  } = params;

  const tmp = mkdtempSync("snapshotserver");
  const chunkDir = path.join(tmp, "chunk");
  mkdirSync(chunkDir);

  let grepKeyword = "world/chunk";
  if (dimension === -1) {
    grepKeyword = "world_nether/DIM-1/chunk";
  } else if (dimension === 1) {
    grepKeyword = "world_the_end/DIM1/chunk";
  }
  const lstree = child_process.spawn("git", ["ls-tree", "-r", hash], {
    cwd: historyDirectory,
  });
  const grep = child_process.spawn("grep", [grepKeyword]);
  const awk = child_process.spawn("awk", ["{print $3, $4}"]);

  lstree.stdout.pipe(grep.stdin);
  lstree.stderr.pipe(process.stderr);
  grep.stdout.pipe(awk.stdin);
  grep.stderr.pipe(process.stderr);
  let dumped = "";
  awk.stdout.on("data", (data) => {
    dumped += data;
  });
  awk.on("close", async () => {
    await checkoutByHash(tmp, dumped, {
      historyDirectory,
      minX,
      maxX,
      minZ,
      maxZ,
    });
    const p = child_process.spawn(core, [
      "-w",
      tmp,
      "-x",
      `${minX}`,
      "-X",
      `${maxX}`,
      "-y",
      `${minY}`,
      "-Y",
      `${maxY}`,
      "-z",
      `${minZ}`,
      "-Z",
      `${maxZ}`,
    ]);
    p.stdout.pipe(res);
    p.on("close", () => {
      rmdir(tmp, { recursive: true }, () => {});
    });
  });
}

function getHistory(historyDirectory: string, core: string) {
  return (req, res) => {
    const { dimension, time, minX, maxX, minY, maxY, minZ, maxZ } = req.query;
    const date = new Date(time * 1000);
    const d = sprintf(
      "%d%02d%02d%02d%02d%02d",
      date.getFullYear(),
      date.getMonth() + 1,
      date.getDate(),
      date.getHours(),
      date.getMinutes(),
      date.getSeconds()
    );
    //NOTE: git log --before を使いたいが, before は commit date に対して効く(--author-date-order を指定したとしても).
    // author date で見たいので自力でソートする.
    const log = child_process.spawn(
      "bash",
      [
        "-c",
        `(echo '${d},x'; git log --pretty='%ad,%H' --author-date-order --date='format:%Y%m%d%H%M%S') | sort | grep -A 1 '${d},x' | tail -1 | cut -d , -f 2`,
      ],
      { cwd: historyDirectory }
    );
    let hash = "";
    log.stdout.on("data", (data) => {
      hash += data;
    });
    log.on("close", () => {
      sendByHash(req, res, {
        core,
        historyDirectory,
        hash: hash.trim(),
        dimension: parseInt(dimension, 10),
        minX,
        maxX,
        minY,
        maxY,
        minZ,
        maxZ,
      });
    });
  };
}

caporal
  .command("run", "start server")
  .option("--port <port>", "port number", caporal.INT, 8001)
  .option("--core <core>", "executable file of the core", caporal.STRING)
  .option("--wild <wild>", "directory for wild snapshot", caporal.STRING)
  .option("--history <history>", "directory for backup history", caporal.STRING)
  .action(async (args, opts) => {
    const port = opts.port;
    const wild = opts.wild;
    const core = opts.core;
    if (!core) {
      throw new Error("'core' not specified");
    }
    if (!wild) {
      throw new Error("'wild' not specified");
    }
    const history = opts.history;
    if (!history) {
      throw new Error(`'history' not specified`);
    }
    const app = express();
    app.get("/wild", getWild(wild, core));
    app.get("/history", getHistory(history, core));
    const http = new Server(app);
    http.listen(port);
  });

caporal.parse(process.argv);
