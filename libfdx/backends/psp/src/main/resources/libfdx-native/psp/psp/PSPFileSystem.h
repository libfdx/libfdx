#pragma once

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#define LIBFDX_PSP_FILE_MAX_PATH 512
#ifndef LIBFDX_PSP_PROJECT_NAME
#define LIBFDX_PSP_PROJECT_NAME "libfdx"
#endif

static int32_t libfdx_psp_to_path(char* output, int32_t outputSize, char16_t* name, int32_t nameSize) {
    int32_t writeIndex = 0;
    int32_t readIndex = 0;
    if (output == 0 || outputSize <= 0 || name == 0 || nameSize <= 0) {
        return 0;
    }
    for (readIndex = 0; readIndex < nameSize && writeIndex < outputSize - 1; readIndex++) {
        char16_t ch = name[readIndex];
        if (ch == '\\') {
            output[writeIndex++] = '/';
        } else if (ch > 0 && ch < 128) {
            output[writeIndex++] = (char) ch;
        } else {
            return 0;
        }
    }
    output[writeIndex] = '\0';
    return writeIndex > 0 ? 1 : 0;
}

static int32_t libfdx_psp_starts_with(const char* value, const char* prefix) {
    size_t prefixLength = 0;
    if (value == 0 || prefix == 0) {
        return 0;
    }
    prefixLength = strlen(prefix);
    return strncmp(value, prefix, prefixLength) == 0;
}

static int32_t libfdx_psp_copy_path(char* output, int32_t outputSize, const char* path) {
    size_t length = 0;
    if (output == 0 || outputSize <= 0 || path == 0) {
        return 0;
    }
    length = strlen(path);
    if (length <= 0 || length >= (size_t) outputSize) {
        return 0;
    }
    memcpy(output, path, length + 1);
    return 1;
}

static int32_t libfdx_psp_existing_path(const char* path, int32_t allowDirectory) {
    SceIoStat info;
    (void) allowDirectory;
    if (path == 0 || path[0] == '\0') {
        return 0;
    }
    memset(&info, 0, sizeof(info));
    return sceIoGetstat(path, &info) >= 0 ? 1 : 0;
}

static int32_t libfdx_psp_try_existing_path(char* output, int32_t outputSize, const char* path,
        int32_t allowDirectory) {
    if (!libfdx_psp_existing_path(path, allowDirectory)) {
        return 0;
    }
    return libfdx_psp_copy_path(output, outputSize, path);
}

static int32_t libfdx_psp_try_relative_assets_path(char* output, int32_t outputSize, const char* path,
        int32_t allowDirectory) {
    char candidate[LIBFDX_PSP_FILE_MAX_PATH];
    if (libfdx_psp_starts_with(path, "assets/")) {
        return 0;
    }
    if (snprintf(candidate, LIBFDX_PSP_FILE_MAX_PATH, "assets/%s", path) >= LIBFDX_PSP_FILE_MAX_PATH) {
        return 0;
    }
    return libfdx_psp_try_existing_path(output, outputSize, candidate, allowDirectory);
}

static int32_t libfdx_psp_try_project_path(char* output, int32_t outputSize, const char* path,
        int32_t allowDirectory) {
    char candidate[LIBFDX_PSP_FILE_MAX_PATH];
    if (snprintf(candidate, LIBFDX_PSP_FILE_MAX_PATH, "ms0:/PSP/GAME/%s/%s",
            LIBFDX_PSP_PROJECT_NAME, path) >= LIBFDX_PSP_FILE_MAX_PATH) {
        return 0;
    }
    if (libfdx_psp_try_existing_path(output, outputSize, candidate, allowDirectory)) {
        return 1;
    }
    if (!libfdx_psp_starts_with(path, "assets/")) {
        if (snprintf(candidate, LIBFDX_PSP_FILE_MAX_PATH, "ms0:/PSP/GAME/%s/assets/%s",
                LIBFDX_PSP_PROJECT_NAME, path) >= LIBFDX_PSP_FILE_MAX_PATH) {
            return 0;
        }
        if (libfdx_psp_try_existing_path(output, outputSize, candidate, allowDirectory)) {
            return 1;
        }
    }
    return 0;
}

static int32_t libfdx_psp_try_device_path(char* output, int32_t outputSize, const char* device,
        const char* path, int32_t allowDirectory) {
    char candidate[LIBFDX_PSP_FILE_MAX_PATH];
    if (snprintf(candidate, LIBFDX_PSP_FILE_MAX_PATH, "%s/%s", device, path) >= LIBFDX_PSP_FILE_MAX_PATH) {
        return 0;
    }
    return libfdx_psp_try_existing_path(output, outputSize, candidate, allowDirectory);
}

static int32_t libfdx_psp_resolve_existing_path(char* output, int32_t outputSize, const char* path,
        int32_t allowDirectory) {
    if (libfdx_psp_try_existing_path(output, outputSize, path, allowDirectory)) {
        return 1;
    }
    if (libfdx_psp_try_relative_assets_path(output, outputSize, path, allowDirectory)) {
        return 1;
    }
    if (libfdx_psp_try_project_path(output, outputSize, path, allowDirectory)) {
        return 1;
    }
    if (libfdx_psp_try_device_path(output, outputSize, "disc0:", path, allowDirectory)) {
        return 1;
    }
    if (libfdx_psp_try_device_path(output, outputSize, "host0:", path, allowDirectory)) {
        return 1;
    }
    return 0;
}

static int32_t libfdx_psp_copy_result(const char* path, char16_t** result) {
    int32_t length = 0;
    int32_t i = 0;
    char16_t* copy = 0;
    if (result == 0 || path == 0) {
        return -1;
    }
    length = (int32_t) strlen(path);
    if (length <= 0) {
        path = ".";
        length = 1;
    }
    copy = (char16_t*) malloc((size_t) length * sizeof(char16_t));
    if (copy == 0) {
        *result = 0;
        return -1;
    }
    for (i = 0; i < length; i++) {
        copy[i] = (char16_t) path[i];
    }
    *result = copy;
    return length;
}

static int32_t libfdx_psp_stat(char16_t* name, int32_t nameSize, struct stat* info) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    SceIoStat pspInfo;
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return 0;
    }
    if (!libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 1)) {
        return 0;
    }
    memset(&pspInfo, 0, sizeof(pspInfo));
    if (sceIoGetstat(resolved, &pspInfo) < 0) {
        return 0;
    }
    if (info != 0) {
        memset(info, 0, sizeof(*info));
        info->st_mode = pspInfo.st_mode;
        info->st_size = pspInfo.st_size;
        info->st_mtime = 0;
    }
    return 1;
}

int32_t teavm_file_homeDirectory(char16_t** result) {
    return libfdx_psp_copy_result(".", result);
}

int32_t teavm_file_workDirectory(char16_t** result) {
    return libfdx_psp_copy_result(".", result);
}

int32_t teavm_file_tempDirectory(char16_t** result) {
    return libfdx_psp_copy_result(".", result);
}

int32_t teavm_file_isFile(char16_t* name, int32_t nameSize) {
    struct stat info;
    return libfdx_psp_stat(name, nameSize, &info) && S_ISREG(info.st_mode);
}

int32_t teavm_file_isDir(char16_t* name, int32_t nameSize) {
    struct stat info;
    return libfdx_psp_stat(name, nameSize, &info) && S_ISDIR(info.st_mode);
}

int32_t teavm_file_exists(char16_t* name, int32_t nameSize) {
    struct stat info;
    return libfdx_psp_stat(name, nameSize, &info);
}

int32_t teavm_file_canRead(char16_t* name, int32_t nameSize) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    SceUID file = -1;
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return 0;
    }
    if (!libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 0)) {
        return 0;
    }
    file = sceIoOpen(resolved, PSP_O_RDONLY, 0777);
    if (file < 0) {
        return 0;
    }
    sceIoClose(file);
    return 1;
}

int32_t teavm_file_canWrite(char16_t* name, int32_t nameSize) {
    (void) name;
    (void) nameSize;
    return 0;
}

TeaVM_StringList* teavm_file_listFiles(char16_t* name, int32_t nameSize) {
    (void) name;
    (void) nameSize;
    return 0;
}

int32_t teavm_file_createDirectory(char16_t* name, int32_t nameSize) {
    (void) name;
    (void) nameSize;
    return 0;
}

int32_t teavm_file_createFile(char16_t* name, int32_t nameSize) {
    (void) name;
    (void) nameSize;
    return 1;
}

int32_t teavm_file_delete(char16_t* name, int32_t nameSize) {
    (void) name;
    (void) nameSize;
    return 0;
}

int32_t teavm_file_rename(char16_t* name, int32_t nameSize, char16_t* newName, int32_t newNameSize) {
    (void) name;
    (void) nameSize;
    (void) newName;
    (void) newNameSize;
    return 0;
}

int64_t teavm_file_lastModified(char16_t* name, int32_t nameSize) {
    struct stat info;
    if (!libfdx_psp_stat(name, nameSize, &info)) {
        return 0;
    }
    return (int64_t) info.st_mtime * 1000LL;
}

int32_t teavm_file_setLastModified(char16_t* name, int32_t nameSize, int64_t lastModified) {
    (void) name;
    (void) nameSize;
    (void) lastModified;
    return 0;
}

int32_t teavm_file_setReadonly(char16_t* name, int32_t nameSize, int32_t readonly) {
    (void) name;
    (void) nameSize;
    (void) readonly;
    return 0;
}

int32_t teavm_file_length(char16_t* name, int32_t nameSize) {
    struct stat info;
    if (!libfdx_psp_stat(name, nameSize, &info)) {
        return 0;
    }
    return (int32_t) info.st_size;
}

int64_t teavm_file_open(char16_t* name, int32_t nameSize, int32_t mode) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    int flags = PSP_O_RDONLY;
    SceUID file = -1;
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return 0;
    }
    if ((mode & 2) != 0) {
        flags = PSP_O_WRONLY | PSP_O_CREAT;
        if ((mode & 4) != 0) {
            flags |= PSP_O_APPEND;
        } else {
            flags |= PSP_O_TRUNC;
        }
    } else if ((mode & 1) != 0) {
        flags = PSP_O_RDONLY;
    }
    if ((mode & 1) != 0 && (mode & 2) == 0) {
        if (!libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 0)) {
            return 0;
        }
        file = sceIoOpen(resolved, flags, 0777);
    } else {
        file = sceIoOpen(path, flags, 0777);
    }
    return file >= 0 ? (int64_t) file : 0;
}

int32_t teavm_file_close(int64_t file) {
    if (file == 0) {
        return 0;
    }
    return sceIoClose((SceUID) file) >= 0 ? 1 : 0;
}

int32_t teavm_file_flush(int64_t file) {
    return file != 0 ? 1 : 0;
}

int32_t teavm_file_seek(int64_t file, int32_t where, int32_t offset) {
    int origin = PSP_SEEK_SET;
    if (file == 0) {
        return 0;
    }
    if (where == 1) {
        origin = PSP_SEEK_CUR;
    } else if (where == 2) {
        origin = PSP_SEEK_END;
    }
    return sceIoLseek((SceUID) file, offset, origin) >= 0 ? 1 : 0;
}

int32_t teavm_file_tell(int64_t file) {
    SceOff position = 0;
    if (file == 0) {
        return -1;
    }
    position = sceIoLseek((SceUID) file, 0, PSP_SEEK_CUR);
    return position >= 0 ? (int32_t) position : -1;
}

int32_t teavm_file_read(int64_t file, int8_t* data, int32_t offset, int32_t count) {
    int read = 0;
    if (file == 0 || data == 0 || offset < 0 || count <= 0) {
        return 0;
    }
    read = sceIoRead((SceUID) file, data + offset, count);
    return read >= 0 ? read : 0;
}

int32_t teavm_file_write(int64_t file, int8_t* data, int32_t offset, int32_t count) {
    (void) file;
    (void) data;
    (void) offset;
    (void) count;
    return 0;
}

int32_t teavm_file_truncate(int64_t file, int32_t size) {
    (void) file;
    (void) size;
    return 0;
}

int32_t teavm_file_isWindows() {
    return 0;
}

int32_t teavm_file_canonicalize(char16_t* name, int32_t nameSize, char16_t** result) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return -1;
    }
    if (libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 1)) {
        return libfdx_psp_copy_result(resolved, result);
    }
    return libfdx_psp_copy_result(path, result);
}

int32_t libfdx_psp_asset_size(char16_t* name, int32_t nameSize) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    SceIoStat info;
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return -1;
    }
    if (!libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 0)) {
        return -1;
    }
    memset(&info, 0, sizeof(info));
    if (sceIoGetstat(resolved, &info) < 0 || info.st_size < 0) {
        return -1;
    }
    return (int32_t) info.st_size;
}

int32_t libfdx_psp_asset_read(char16_t* name, int32_t nameSize, int8_t* target, int32_t targetLength) {
    char path[LIBFDX_PSP_FILE_MAX_PATH];
    char resolved[LIBFDX_PSP_FILE_MAX_PATH];
    SceUID file = -1;
    int32_t total = 0;
    if (target == 0 || targetLength < 0) {
        return -1;
    }
    if (!libfdx_psp_to_path(path, LIBFDX_PSP_FILE_MAX_PATH, name, nameSize)) {
        return -1;
    }
    if (!libfdx_psp_resolve_existing_path(resolved, LIBFDX_PSP_FILE_MAX_PATH, path, 0)) {
        return -1;
    }
    file = sceIoOpen(resolved, PSP_O_RDONLY, 0777);
    if (file < 0) {
        return -1;
    }
    while (total < targetLength) {
        int read = sceIoRead(file, target + total, targetLength - total);
        if (read < 0) {
            sceIoClose(file);
            return -1;
        }
        if (read == 0) {
            break;
        }
        total += read;
    }
    sceIoClose(file);
    return total;
}

int32_t libfdx_psp_debug_log(char16_t* message, int32_t messageSize) {
    char line[1024];
    int32_t i = 0;
    int32_t length = 0;
    SceUID file = -1;
    if (message == 0 || messageSize <= 0) {
        return 0;
    }
    sceIoMkdir("ms0:/PSP/SYSTEM/DUMP", 0777);
    file = sceIoOpen("ms0:/PSP/SYSTEM/DUMP/libfdx-app.log",
            PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    if (file < 0) {
        file = sceIoOpen("libfdx-app.log", PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    }
    if (file < 0) {
        return 0;
    }
    for (i = 0; i < messageSize && length < (int32_t) sizeof(line) - 2; i++) {
        char16_t ch = message[i];
        line[length++] = (ch > 0 && ch < 128) ? (char) ch : '?';
    }
    line[length++] = '\n';
    sceIoWrite(file, line, length);
    sceIoClose(file);
    return 1;
}

int32_t libfdx_psp_debug_log_clear() {
    sceIoMkdir("ms0:/PSP/SYSTEM/DUMP", 0777);
    sceIoRemove("ms0:/PSP/SYSTEM/DUMP/libfdx-app.log");
    sceIoRemove("libfdx-app.log");
    return 1;
}
