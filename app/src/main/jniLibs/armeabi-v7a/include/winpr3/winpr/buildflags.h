#ifndef WINPR_BUILD_FLAGS_H
#define WINPR_BUILD_FLAGS_H

#define WINPR_CFLAGS "-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -march=armv7-a -mthumb -Wformat -Werror=format-security  -fvisibility=hidden -fno-omit-frame-pointer -Wredundant-decls -fsigned-char -Wimplicit-function-declaration -fvisibility=hidden -fno-limit-debug-info "
#define WINPR_COMPILER_ID "Clang"
#define WINPR_COMPILER_VERSION "21.0.0"
#define WINPR_TARGET_ARCH "ARM"
#define WINPR_BUILD_CONFIG ""
#define WINPR_BUILD_TYPE "Debug"

#endif /* WINPR_BUILD_FLAGS_H */
