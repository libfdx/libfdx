#define _POSIX_C_SOURCE 200809L
#include "libfdx_test_process.h"
#include <assert.h>
#include <stdio.h>
#include <time.h>
#ifndef _WIN32
#include <sys/stat.h>
#endif
#ifdef _WIN32
#include <direct.h>
#endif

static void delay(void) {
#ifdef _WIN32
    Sleep(20);
#else
    struct timespec duration = {0, 20000000};
    nanosleep(&duration, NULL);
#endif
}
static void finish(void* tracker) {
    int count;
    for (count = 0; count < 250 && fdxTestProcessPoll(tracker); count++) delay();
    assert(!fdxTestProcessPoll(tracker));
    assert(((FdxTestProcesses*) tracker)->children == NULL);
}
static void expectMarker(const char* name) {
    FILE* marker = fopen(name, "rb");
    assert(marker);
    fclose(marker);
}
static void launch(void* tracker, const char* marker) {
    const char payload[] = "child\0MARKER\0argument with spaces\0quote\"inside\0slash\\\"quote\0tail\\\\\0\0caf\xc3\xa9 \xe2\x98\x83\0";
    unsigned char packed[sizeof(payload) + 100];
    size_t prefix = 6, markerSize = strlen(marker) + 1;
    size_t suffix = sizeof(payload) - 1 - 13;
    memcpy(packed, payload, prefix);
    memcpy(packed + prefix, marker, markerSize);
    memcpy(packed + prefix + markerSize, payload + 13, suffix);
    assert(!fdxTestProcessLaunch(tracker, packed, (int) (prefix + markerSize + suffix)));
}

#ifdef _WIN32
int wmain(int argc, wchar_t** argv) {
    if (argc > 1) {
        FILE* file;
        wchar_t cwd[32768];
        assert(argc == 9 && !wcscmp(argv[1], L"child"));
        assert(!wcscmp(argv[3], L"argument with spaces"));
        assert(!wcscmp(argv[4], L"quote\"inside"));
        assert(!wcscmp(argv[5], L"slash\\\"quote"));
        assert(!wcscmp(argv[6], L"tail\\\\"));
        assert(!wcscmp(argv[7], L""));
        assert(!wcscmp(argv[8], L"caf\u00e9 \u2603"));
        assert(_wgetcwd(cwd, 32768) && wcsstr(cwd, L"cwd \u00fc spaced"));
        file = _wfopen(argv[2], L"wb");
        assert(file); fputs("child completed", file); fclose(file);
        puts("PROCESS_CHILD_OUT"); fputs("PROCESS_CHILD_ERR\n", stderr);
        fflush(stdout); fflush(stderr);
        Sleep(300);
        return 7;
    }
    CreateDirectoryW(L"cwd \u00fc spaced", NULL);
    assert(SetCurrentDirectoryW(L"cwd \u00fc spaced"));
#else
int main(int argc, char** argv) {
    if (argc > 1) {
        FILE* file;
        char cwd[8192];
        assert(argc == 9 && !strcmp(argv[1], "child"));
        assert(!strcmp(argv[3], "argument with spaces"));
        assert(!strcmp(argv[4], "quote\"inside"));
        assert(!strcmp(argv[5], "slash\\\"quote"));
        assert(!strcmp(argv[6], "tail\\\\"));
        assert(!strcmp(argv[7], ""));
        assert(!strcmp(argv[8], "caf\xc3\xa9 \xe2\x98\x83"));
        assert(getcwd(cwd, sizeof(cwd)) && strstr(cwd, "cwd \xc3\xbc spaced"));
        file = fopen(argv[2], "wb");
        assert(file); fputs("child completed", file); fclose(file);
        puts("PROCESS_CHILD_OUT"); fputs("PROCESS_CHILD_ERR\n", stderr);
        fflush(stdout); fflush(stderr);
        for (int i = 0; i < 15; i++) delay();
        return 7;
    }
    mkdir("cwd \xc3\xbc spaced", 0700);
    assert(!chdir("cwd \xc3\xbc spaced"));
#endif
    {
        void* tracker = fdxTestProcessCreate();
        assert(tracker);
        assert(fdxTestProcessLaunch(tracker, (const unsigned char*) "unterminated", 12) == EINVAL);
#ifdef _WIN32
        assert(fdxTestProcessLaunch(tracker, (const unsigned char*) "\xff\0", 2) != 0);
#endif
        assert(!fdxTestProcessPoll(tracker));
        launch(tracker, "one.txt");
        launch(tracker, "two.txt");
        assert(((FdxTestProcesses*) tracker)->children->next);
        assert(fdxTestProcessPoll(tracker) == 1);
        finish(tracker);
        expectMarker("one.txt"); expectMarker("two.txt");
        launch(tracker, "three.txt");
        assert(fdxTestProcessPoll(tracker) == 1);
        finish(tracker);
        expectMarker("three.txt");
        launch(tracker, "four.txt");
        fdxTestProcessDispose(tracker);
        for (int i = 0; i < 30; i++) delay();
        expectMarker("four.txt");
        fdxTestProcessDispose(NULL);
        puts("libfdx_test_process_test: Unicode argv/path, cwd, concurrent children, exit and relaunch passed");
    }
    return 0;
}
