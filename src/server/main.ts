import * as express from "express";
import { Server } from "http";

const app = express();
const http = new Server(app);
http.listen(8001);
