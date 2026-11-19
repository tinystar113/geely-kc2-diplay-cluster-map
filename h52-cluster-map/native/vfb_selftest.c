/*
 * vfb_selftest: on-car test of vfb_renderer (the core of libdiplay_vfb.so).
 *
 *   vfb_selftest cycle <seconds>  open -> moving bars -> close, twice (3 s gap); prints "cycle N ok"
 *   vfb_selftest grid  <seconds>  calibration grid (see vfb_renderer.h) for the cluster's visible area
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "vfb_renderer.h"

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

static int run_bars(int seconds) {
    double start = now_s();
    int frames = 0;
    while (now_s() - start < seconds) {
        if (vfb_draw_bars((int)(now_s() - start)) != 0) return -1;
        frames++;
        usleep(33000);
    }
    return frames;
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    const char *mode = argc > 1 ? argv[1] : "cycle";
    int seconds = argc > 2 ? atoi(argv[2]) : 10;

    if (vfb_open() != 0) { printf("open failed\n"); return 2; }
    if (vfb_open() != 0) { printf("second open failed (must be a no-op)\n"); return 2; }

    if (strcmp(mode, "grid") == 0) {
        double start = now_s();
        while (now_s() - start < seconds) {         /* keep swapping: the cluster stream needs frames */
            if (vfb_draw_grid() != 0) { printf("grid draw failed\n"); return 3; }
            usleep(66000);
        }
        printf("grid done\n");
        return 0;                                   /* exit with the surface still open (never torn down) */
    }

    /* Stream on -> off (just stop drawing, no teardown) -> on again, all on one open surface. */
    for (int cycle = 1; cycle <= 2; cycle++) {
        printf("cycle %d drawing %dx%d for %d s\n", cycle, vfb_width(), vfb_height(), seconds);
        int frames = run_bars(seconds);
        if (frames <= 0) { printf("cycle %d draw failed\n", cycle); return 3; }
        printf("cycle %d ok frames=%d\n", cycle, frames);
        if (cycle == 1) sleep(3);
    }
    printf("exiting with surface open\n");
    return 0;
}
