import * as express from "express";
import { Server } from "http";
import * as caporal from "caporal";
import * as path from "path";
import * as child_process from "child_process";

caporal
  .command("run", "start server")
  .option("--port <port>", "port number", caporal.INT, 8001)
  .option("--wild <wild>", "directory for wild snapshot", caporal.STRING)
  .action(async (args, opts) => {
    const port = opts.port ?? 8000;
    const wild = opts.wild;
    if (!wild) {
      throw new Error("'wild' not specified");
    }
    const app = express();
    app.get("/wild", (req, res) => {
      try {
        const {
          version,
          dimension,
          minX,
          maxX,
          minY,
          maxY,
          minZ,
          maxZ
        } = req.query;
        const core = path.join(__dirname, "..", "core", "build", "core");
        const world = path.join(wild, version, `${dimension}`);
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
          maxZ
        ]);
        p.stdout.pipe(res);
      } catch (e) {
        res.status(500).send(`{status:"fatal error"}`);
      }
    });
    app.get("/history", () => {
      //TODO
    });
    const http = new Server(app);
    http.listen(port);
  });

caporal.parse(process.argv);
