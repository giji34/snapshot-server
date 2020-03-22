package com.github.giji34.worldgen;

import java.util.logging.Logger;

interface Task {
    void run();
    void cancel();
    boolean isFinished();
    void printLog(Logger logger);
}

