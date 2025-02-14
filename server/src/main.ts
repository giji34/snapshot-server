import express = require("express");
import { Request, Response } from "express";
import { Server } from "http";
import caporal = require("caporal");
import * as path from "path";
import * as child_process from "child_process";
import { sprintf } from "sprintf-js";
import { mkdirSync, mkdtempSync, rmdir } from "fs";
import * as fs from "fs";

function getWild(wildDirectory: string, core: string) {
  return (req: Request, res: Response) => {
    try {
      const { minX, maxX, minY, maxY, minZ, maxZ } = req.query;
      let version = req.query["version"] as string;
      if (fs.existsSync(path.join(wildDirectory, version + "p1.18"))) {
        version = version + "p1.18";
      }
      const dimension = req.query["dimension"] as string;
      const dim = parseInt(dimension as string, 10);
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
        minX as string,
        "-X",
        maxX as string,
        "-y",
        minY as string,
        "-Y",
        maxY as string,
        "-z",
        minZ as string,
        "-Z",
        maxZ as string,
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
  req: Request,
  res: Response,
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
  return (req: Request, res: Response) => {
    const { dimension, time, minX, maxX, minY, maxY, minZ, maxZ } = req.query;
    const date = new Date(parseInt(time as string, 10) * 1000);
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
        dimension: parseInt(dimension as string, 10),
        minX: parseInt(minX as string, 10),
        maxX: parseInt(maxX as string, 10),
        minY: parseInt(minY as string, 10),
        maxY: parseInt(maxY as string, 10),
        minZ: parseInt(minZ as string, 10),
        maxZ: parseInt(maxZ as string, 10),
      });
    });
  };
}

caporal
  .command("run", "start server")
  .option(
    "--core <core>",
    "executable file of the core",
    caporal.STRING | caporal.REQUIRED
  )
  .option(
    "--server <server>",
    `server config in format: "port:wild:history" where "port": port number, "wild": directory for wild snapshot, "history": directory for backup history`,
    caporal.LIST | caporal.REPEATABLE | caporal.REQUIRED
  )
  .action(async (args, opts) => {
    const core = opts.core;
    if (!core) {
      throw new Error("'core' not specified");
    }
    for (const server of opts.server as string[]) {
      const [port, wild, history] = server.split(":");
      const p = parseInt(port, 10);
      const app = express();
      app.get("/wild", getWild(wild, core));
      app.get("/history", getHistory(history, core));
      const http = new Server(app);
      http.listen(p);
    }
  });

caporal.parse(process.argv);
