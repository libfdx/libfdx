package io.github.libfdx.backend.iosc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Writes iOS C project output.
 *
 * @author xpenatan
 */
public final class IosCProjectWriter {
    private static final List<String> NATIVE_RESOURCE_PREFIXES = List.of(
            "libfdx-native/shared/",
            "libfdx-native/ios/");

    private IosCProjectWriter() {
    }

    /**
     * Runs the write step.
     *
     * @param project the project
     * @return the written files
     * @throws IOException if the operation cannot be completed
     */
    public static Set<Path> write(IosCProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        Path root = project.getBuildRoot();
        Path sources = project.getGeneratedSourcesDirectory();
        Path release = project.getReleaseDirectory();
        Files.createDirectories(root);
        Files.createDirectories(sources);
        Files.createDirectories(release);
        copyNativeResources(project.getNativeResourceClasspath(), root.resolve("c/external_cpp"));
        written.addAll(copyAssets(project.getAssets(), release.resolve("assets")));
        written.add(writeFile(sources.resolve("app_include.c"), appInclude(project.getGraphicsApi())));
        written.add(writeFile(sources.resolve("ios_bridge.h"), iosBridgeHeader()));
        written.add(writeFile(sources.resolve("ios_bridge.c"), iosBridgeSource()));
        written.add(writeFile(root.resolve("CMakeLists.txt"), cmake(project)));
        written.addAll(writeXcodeProject(project));
        return Set.copyOf(written);
    }

    private static Set<Path> writeXcodeProject(IosCProject project) throws IOException {
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        String xcodeName = xcodeName(project.getProjectName());
        Path root = project.getXcodeProjectDirectory();
        Path sources = root.resolve("Sources");
        Path projectFile = root.resolve(xcodeName + ".xcodeproj/project.pbxproj");
        deleteDirectory(root);
        Files.createDirectories(sources);
        Files.createDirectories(projectFile.getParent());
        written.add(writeFile(sources.resolve("LibfdxIOSCApp.swift"), swiftApp()));
        written.add(writeFile(sources.resolve("TeaVMViewController.swift"),
                swiftViewController(project.getGraphicsApi())));
        written.add(writeFile(sources.resolve("LibfdxIOSC-Bridging-Header.h"), bridgingHeader(project.getGraphicsApi())));
        written.add(writeFile(sources.resolve("Info.plist"), infoPlist(project.getProjectName())));
        written.add(writeFile(sources.resolve("LaunchScreen.storyboard"), launchScreen()));
        written.add(writeFile(projectFile, pbxproj(xcodeName, project.getBundleIdentifier(), project.getGraphicsApi())));
        written.add(writeFile(root.resolve("README.md"), readme(xcodeName, project.getGraphicsApi())));
        return Set.copyOf(written);
    }

    private static Path writeFile(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path.toAbsolutePath().normalize();
    }

    private static String appInclude(IosCGraphicsApi graphicsApi) {
        return """
                #if defined(__has_include)
                #  if __has_include("teavm_optimizations.h")
                #    include "teavm_optimizations.h"
                #  endif
                #else
                #  include "teavm_optimizations.h"
                #endif

                #ifndef TEAVM_GENERATED_SHORT_FILE_NAMES
                #define TEAVM_GENERATED_SHORT_FILE_NAMES 1
                #endif

                #if defined(__APPLE__)
                @OPENGL_INCLUDE@
                #include <signal.h>
                #include <stdint.h>
                #include <stdio.h>
                #include <stdlib.h>
                #include <string.h>

                static int libfdx_ios_c_sigwaitinfo(const sigset_t* signals, siginfo_t* info) {
                    int signal = 0;
                    int result = sigwait(signals, &signal);
                    if(result != 0) {
                        return -1;
                    }
                    if(info != NULL) {
                        memset(info, 0, sizeof(siginfo_t));
                        info->si_signo = signal;
                    }
                    return signal;
                }

                #define sigwaitinfo libfdx_ios_c_sigwaitinfo

                @OPENGL_SHADER_SOURCE@

                static void libfdx_ios_c_log(const char* message) {
                    if(message != NULL) {
                        fprintf(stderr, "%s\\n", message);
                        fflush(stderr);
                    }
                }
                #endif

                #define main libfdx_ios_c_teavm_main
                #include "all.c"
                #undef main

                #if defined(__has_include)
                #  if __has_include("../external_cpp/teavm_optimizations/teavm/teavm_fastmath.c")
                #    include "../external_cpp/teavm_optimizations/teavm/teavm_fastmath.c"
                #  endif
                #  if __has_include("../external_cpp/teavm_optimizations/teavm/teavm_matrix4.c")
                #    include "../external_cpp/teavm_optimizations/teavm/teavm_matrix4.c"
                #  endif
                #endif

                #include "ios_bridge.c"
                """
                .replace("@OPENGL_INCLUDE@", graphicsApi.isMetal() ? "" : "#include <OpenGLES/ES3/gl.h>")
                .replace("@OPENGL_SHADER_SOURCE@", graphicsApi.isMetal() ? "" : """
                static void libfdx_ios_c_glShaderSource(GLuint shader, GLsizei length, const int32_t* sourceData) {
                    if(sourceData == NULL || length < 0) {
                        return;
                    }
                    char* source = (char*) malloc((size_t) length + 1);
                    if(source == NULL) {
                        return;
                    }
                    for(GLsizei i = 0; i < length; i++) {
                        source[i] = (char) (sourceData[i] & 0xff);
                    }
                    source[length] = '\\0';
                    const GLchar* strings[1] = { source };
                    glShaderSource(shader, 1, strings, NULL);
                    free(source);
                }
                """);
    }

    private static String iosBridgeHeader() {
        return """
                #ifndef LIBFDX_IOS_C_BRIDGE_H
                #define LIBFDX_IOS_C_BRIDGE_H

                #include <stdint.h>

                #ifdef __cplusplus
                extern "C" {
                #endif

                int32_t libfdx_ios_c_start(const char* workingDirectory);
                void libfdx_ios_c_resize(int32_t width, int32_t height, float scale);
                void libfdx_ios_c_render(void);
                void libfdx_ios_c_pause(void);
                void libfdx_ios_c_resume(void);
                void libfdx_ios_c_dispose(void);
                void libfdx_ios_c_touch(int32_t type, int32_t pointer, int32_t x, int32_t y, float pressure);
                int32_t libfdx_ios_c_status_code(void);

                #ifdef __cplusplus
                }
                #endif

                #endif
                """;
    }

    private static String iosBridgeSource() {
        return """
                #include "ios_bridge.h"

                #if !defined(_WIN32)
                #include <unistd.h>
                #endif

                int libfdx_ios_c_teavm_main(int argc, char** argv);

                int32_t libfdx_ios_c_start(const char* workingDirectory) {
                #if !defined(_WIN32)
                    if(workingDirectory != 0 && workingDirectory[0] != '\\0') {
                        chdir(workingDirectory);
                    }
                #else
                    (void) workingDirectory;
                #endif
                    char* argv[] = { "libfdx-ios-c", (char*) workingDirectory };
                    int argc = workingDirectory != 0 && workingDirectory[0] != '\\0' ? 2 : 1;
                    return (int32_t) libfdx_ios_c_teavm_main(argc, argv);
                }
                """;
    }

    private static String cmake(IosCProject project) {
        boolean metal = project.getGraphicsApi().isMetal();
        String projectName = cmakeIdentifier(project.getProjectName());
        return """
                cmake_minimum_required(VERSION 3.14)
                project(@PROJECT_NAME@ @PROJECT_LANGUAGES@)

                set(CMAKE_C_STANDARD 11)
                set(CMAKE_C_STANDARD_REQUIRED ON)
                @OBJCXX_SETTINGS@

                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp")
                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/native_optimizations")
                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/teavm_optimizations/teavm")
                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/teavm_stats")
                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/stb/include")
                include_directories("${CMAKE_CURRENT_SOURCE_DIR}/c/src")

                add_library(@PROJECT_NAME@ STATIC
                    "${CMAKE_CURRENT_SOURCE_DIR}/c/src/app_include.c"@NATIVE_SOURCES@)

                if(APPLE)
                @APPLE_FRAMEWORKS@
                endif()
                """
                .replace("@PROJECT_NAME@", projectName)
                .replace("@PROJECT_LANGUAGES@", metal ? "C OBJCXX" : "C")
                .replace("@OBJCXX_SETTINGS@", metal ? """
                set(CMAKE_OBJCXX_STANDARD 17)
                set(CMAKE_OBJCXX_STANDARD_REQUIRED ON)
                set_source_files_properties("${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/libfdx_ios_metal.mm"
                    PROPERTIES COMPILE_FLAGS "-fobjc-arc")
                """ : "")
                .replace("@NATIVE_SOURCES@", metal
                        ? "\n    \"${CMAKE_CURRENT_SOURCE_DIR}/c/external_cpp/libfdx_ios_metal.mm\""
                        : "")
                .replace("@APPLE_FRAMEWORKS@", metal
                        ? "    target_link_libraries(" + projectName + " \"-framework Metal\" "
                                + "\"-framework MetalKit\" \"-framework QuartzCore\" \"-framework Foundation\")"
                        : "    target_link_libraries(" + projectName + " \"-framework OpenGLES\")");
    }

    private static String swiftApp() {
        return """
                import SwiftUI

                @main
                struct LibfdxIOSCApp: App {
                    var body: some Scene {
                        WindowGroup {
                            TeaVMContainerView()
                                .ignoresSafeArea()
                        }
                    }
                }

                struct TeaVMContainerView: UIViewControllerRepresentable {
                    func makeUIViewController(context: Context) -> TeaVMViewController {
                        TeaVMViewController()
                    }

                    func updateUIViewController(_ uiViewController: TeaVMViewController, context: Context) {
                    }
                }
                """;
    }

    private static String swiftViewController(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return swiftMetalViewController();
        }
        return swiftGlesViewController();
    }

    private static String swiftGlesViewController() {
        return """
                import GLKit
                import UIKit

                final class TeaVMViewController: GLKViewController {
                    private let glContext: EAGLContext
                    private var started = false
                    private var lastWidth: Int32 = 0
                    private var lastHeight: Int32 = 0
                    private var lastScale: Float = 1.0
                    private let statusLabel = UILabel()

                    init() {
                        guard let context = EAGLContext(api: .openGLES3) else {
                            fatalError("OpenGLES 3 is required")
                        }
                        glContext = context
                        super.init(nibName: nil, bundle: nil)
                    }

                    required init?(coder: NSCoder) {
                        guard let context = EAGLContext(api: .openGLES3) else {
                            return nil
                        }
                        glContext = context
                        super.init(coder: coder)
                    }

                    override func loadView() {
                        view = GLKView(frame: .zero, context: glContext)
                    }

                    override func viewDidLoad() {
                        super.viewDidLoad()
                        preferredFramesPerSecond = 60
                        EAGLContext.setCurrent(glContext)
                        if let glView = view as? GLKView {
                            glView.drawableColorFormat = .RGBA8888
                            glView.drawableDepthFormat = .format24
                            glView.isMultipleTouchEnabled = true
                        }
                        statusLabel.textColor = .white
                        statusLabel.backgroundColor = UIColor.black.withAlphaComponent(0.65)
                        statusLabel.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
                        statusLabel.numberOfLines = 0
                        statusLabel.isHidden = true
                        statusLabel.translatesAutoresizingMaskIntoConstraints = false
                        view.addSubview(statusLabel)
                        NSLayoutConstraint.activate([
                            statusLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 8),
                            statusLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -8),
                            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
                        ])
                    }

                    override func viewDidLayoutSubviews() {
                        super.viewDidLayoutSubviews()
                        notifyResizeIfNeeded()
                    }

                    override func glkView(_ view: GLKView, drawIn rect: CGRect) {
                        EAGLContext.setCurrent(glContext)
                        startIfNeeded()
                        notifyResizeIfNeeded()
                        libfdx_ios_c_render()
                        updateStatus()
                    }

                    override func viewWillAppear(_ animated: Bool) {
                        super.viewWillAppear(animated)
                        libfdx_ios_c_resume()
                    }

                    override func viewWillDisappear(_ animated: Bool) {
                        libfdx_ios_c_pause()
                        super.viewWillDisappear(animated)
                    }

                    deinit {
                        libfdx_ios_c_dispose()
                        if EAGLContext.current() === glContext {
                            EAGLContext.setCurrent(nil)
                        }
                    }

                    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 0)
                    }

                    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 1)
                    }

                    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 2)
                    }

                    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 2)
                    }

                    private func startIfNeeded() {
                        if started {
                            return
                        }
                        started = true
                        let assetPath = Bundle.main.resourcePath?.appending("/assets") ?? ""
                        assetPath.withCString { path in
                            _ = libfdx_ios_c_start(path)
                        }
                    }

                    private func notifyResizeIfNeeded() {
                        let scale = Float(view.window?.screen.scale ?? UIScreen.main.scale)
                        let width = Int32(max(1, Int(round(view.bounds.width * CGFloat(scale)))))
                        let height = Int32(max(1, Int(round(view.bounds.height * CGFloat(scale)))))
                        if width == lastWidth && height == lastHeight && scale == lastScale {
                            return
                        }
                        lastWidth = width
                        lastHeight = height
                        lastScale = scale
                        libfdx_ios_c_resize(width, height, scale)
                    }

                    private func sendTouches(_ touches: Set<UITouch>, type: Int32) {
                        for touch in touches {
                            let location = touch.location(in: view)
                            let pointer = Int32(abs(ObjectIdentifier(touch).hashValue % 1_000_000))
                            libfdx_ios_c_touch(type, pointer, Int32(location.x.rounded()),
                                                Int32(location.y.rounded()), Float(touch.force > 0 ? touch.force : 1.0))
                        }
                    }

                    private func updateStatus() {
                        let code = libfdx_ios_c_status_code()
                        statusLabel.isHidden = code >= 0
                        if code < 0 {
                            statusLabel.text = "libFDX iOS C error \\(code)"
                        }
                    }
                }
                """;
    }

    private static String swiftMetalViewController() {
        return """
                import MetalKit
                import UIKit

                final class TeaVMViewController: UIViewController, MTKViewDelegate {
                    private let maxPointers = 20
                    private let statusLabel = UILabel()
                    private var started = false
                    private var lastWidth: Int32 = 0
                    private var lastHeight: Int32 = 0
                    private var lastScale: Float = 1.0
                    private var touchPointers: [ObjectIdentifier: Int32] = [:]
                    private var availablePointers: [Int32] = Array(0..<20).map(Int32.init)

                    private var metalView: MTKView {
                        view as! MTKView
                    }

                    override func loadView() {
                        guard let device = MTLCreateSystemDefaultDevice() else {
                            fatalError("Metal is required")
                        }
                        let createdView = MTKView(frame: UIScreen.main.bounds, device: device)
                        createdView.contentScaleFactor = UIScreen.main.scale
                        createdView.colorPixelFormat = .bgra8Unorm
                        createdView.depthStencilPixelFormat = .invalid
                        createdView.framebufferOnly = false
                        createdView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
                        createdView.preferredFramesPerSecond = 60
                        createdView.isPaused = false
                        createdView.enableSetNeedsDisplay = false
                        createdView.autoResizeDrawable = true
                        createdView.isMultipleTouchEnabled = true
                        createdView.delegate = self
                        view = createdView
                        libfdx_ios_metal_set_view(Unmanaged.passUnretained(createdView).toOpaque())
                    }

                    override func viewDidLoad() {
                        super.viewDidLoad()
                        view.backgroundColor = .black
                        setupStatusLabel()
                        startIfNeeded()
                        notifyResizeIfNeeded()
                    }

                    override func viewDidLayoutSubviews() {
                        super.viewDidLayoutSubviews()
                        notifyResizeIfNeeded()
                    }

                    override func viewWillAppear(_ animated: Bool) {
                        super.viewWillAppear(animated)
                        libfdx_ios_c_resume()
                        metalView.isPaused = false
                    }

                    override func viewWillDisappear(_ animated: Bool) {
                        metalView.isPaused = true
                        libfdx_ios_c_pause()
                        super.viewWillDisappear(animated)
                    }

                    deinit {
                        libfdx_ios_c_dispose()
                        libfdx_ios_metal_set_view(nil)
                    }

                    override var prefersStatusBarHidden: Bool {
                        true
                    }

                    func draw(in view: MTKView) {
                        startIfNeeded()
                        notifyResizeIfNeeded()
                        libfdx_ios_c_render()
                        updateStatus()
                    }

                    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
                        notifyResizeIfNeeded(size: size, scale: Float(view.contentScaleFactor))
                    }

                    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 0, releasePointers: false)
                    }

                    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 1, releasePointers: false)
                    }

                    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 2, releasePointers: true)
                    }

                    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
                        sendTouches(touches, type: 2, releasePointers: true)
                    }

                    private func setupStatusLabel() {
                        statusLabel.translatesAutoresizingMaskIntoConstraints = false
                        statusLabel.textColor = .white
                        statusLabel.backgroundColor = UIColor.black.withAlphaComponent(0.65)
                        statusLabel.font = UIFont.monospacedSystemFont(ofSize: 12, weight: .regular)
                        statusLabel.numberOfLines = 0
                        statusLabel.isHidden = true
                        view.addSubview(statusLabel)
                        NSLayoutConstraint.activate([
                            statusLabel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 8),
                            statusLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -8),
                            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8)
                        ])
                    }

                    private func startIfNeeded() {
                        if started {
                            return
                        }
                        started = true
                        let assetPath = Bundle.main.resourcePath?.appending("/assets") ?? ""
                        assetPath.withCString { path in
                            _ = libfdx_ios_c_start(path)
                        }
                    }

                    private func notifyResizeIfNeeded() {
                        let scale = Float(view.window?.screen.scale ?? UIScreen.main.scale)
                        notifyResizeIfNeeded(size: metalView.drawableSize, scale: scale)
                    }

                    private func notifyResizeIfNeeded(size: CGSize, scale: Float) {
                        let width = Int32(max(1, Int(round(size.width))))
                        let height = Int32(max(1, Int(round(size.height))))
                        if width == lastWidth && height == lastHeight && scale == lastScale {
                            return
                        }
                        lastWidth = width
                        lastHeight = height
                        lastScale = scale
                        libfdx_ios_c_resize(width, height, scale)
                    }

                    private func sendTouches(_ touches: Set<UITouch>, type: Int32, releasePointers: Bool) {
                        let scale = view.window?.screen.scale ?? UIScreen.main.scale
                        for touch in touches {
                            let pointer = pointerIndex(for: touch)
                            let location = touch.location(in: view)
                            let pressure = normalizedPressure(for: touch)
                            libfdx_ios_c_touch(type, pointer, Int32((location.x * scale).rounded()),
                                                Int32((location.y * scale).rounded()), pressure)
                            if releasePointers {
                                releasePointer(for: touch)
                            }
                        }
                    }

                    private func pointerIndex(for touch: UITouch) -> Int32 {
                        let id = ObjectIdentifier(touch)
                        if let existing = touchPointers[id] {
                            return existing
                        }
                        let pointer = availablePointers.isEmpty
                            ? Int32(touchPointers.count % maxPointers)
                            : availablePointers.removeFirst()
                        touchPointers[id] = pointer
                        return pointer
                    }

                    private func releasePointer(for touch: UITouch) {
                        let id = ObjectIdentifier(touch)
                        guard let pointer = touchPointers.removeValue(forKey: id) else {
                            return
                        }
                        availablePointers.append(pointer)
                        availablePointers.sort()
                    }

                    private func normalizedPressure(for touch: UITouch) -> Float {
                        if touch.maximumPossibleForce > 0 {
                            return Float(touch.force / touch.maximumPossibleForce)
                        }
                        return 1.0
                    }

                    private func updateStatus() {
                        let code = libfdx_ios_c_status_code()
                        statusLabel.isHidden = code >= 0
                        if code < 0 {
                            statusLabel.text = "libFDX iOS C error \\(code)"
                        }
                    }
                }
                """;
    }

    private static String bridgingHeader(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return """
                    #include "../../c/src/ios_bridge.h"
                    #include "../../c/external_cpp/libfdx_ios_metal.h"
                    """;
        }
        return """
                #include "../../c/src/ios_bridge.h"
                """;
    }

    private static String infoPlist(String projectName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
                  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleDisplayName</key>
                    <string>@DISPLAY_NAME@</string>
                    <key>UILaunchStoryboardName</key>
                    <string>LaunchScreen</string>
                    <key>UISupportedInterfaceOrientations</key>
                    <array>
                        <string>UIInterfaceOrientationPortrait</string>
                        <string>UIInterfaceOrientationLandscapeLeft</string>
                        <string>UIInterfaceOrientationLandscapeRight</string>
                    </array>
                </dict>
                </plist>
                """.replace("@DISPLAY_NAME@", xml(projectName));
    }

    private static String launchScreen() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0"
                    toolsVersion="22155" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none"
                    useAutolayout="YES" launchScreen="YES" useTraitCollections="YES"
                    colorMatched="YES" initialViewController="01J-lp-oVM">
                    <scenes>
                        <scene sceneID="EHf-IW-A2E">
                            <objects>
                                <viewController id="01J-lp-oVM" sceneMemberID="viewController">
                                    <view key="view" contentMode="scaleToFill" id="Ze5-6b-2t3">
                                        <rect key="frame" x="0.0" y="0.0" width="390" height="844"/>
                                        <color key="backgroundColor" red="0.06" green="0.07" blue="0.08" alpha="1"
                                            colorSpace="custom" customColorSpace="sRGB"/>
                                    </view>
                                </viewController>
                                <placeholder placeholderIdentifier="IBFirstResponder" id="iYj-Kq-Ea1"
                                    userLabel="First Responder" sceneMemberID="firstResponder"/>
                            </objects>
                        </scene>
                    </scenes>
                </document>
                """;
    }

    private static String readme(String xcodeName, IosCGraphicsApi graphicsApi) {
        String graphicsNotes = graphicsApi.isMetal()
                ? """

                This project uses the native iOS Metal / MetalKit graphics path.
                """
                : """

                This project uses the native iOS OpenGLES / GLKit graphics path.
                """;
        return ("""
                # libFDX iOS C

                Open @XCODE_NAME@.xcodeproj in Xcode after running the Gradle iosC generation task.
                The project compiles the generated TeaVM C source from ../c/src/app_include.c and bundles assets from ../c/release/assets.
                """ + graphicsNotes).replace("@XCODE_NAME@", xcodeName);
    }

    private static String pbxproj(String xcodeName, String bundleIdentifier, IosCGraphicsApi graphicsApi) {
        return """
                // !$*UTF8*$!
                {
                    archiveVersion = 1;
                    classes = {
                    };
                    objectVersion = 56;
                    objects = {

                /* Begin PBXBuildFile section */
                        1E2A00000000000000000001 /* LibfdxIOSCApp.swift in Sources */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000011 /* LibfdxIOSCApp.swift */; };
                        1E2A00000000000000000002 /* TeaVMViewController.swift in Sources */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000012 /* TeaVMViewController.swift */; };
                        1E2A00000000000000000003 /* app_include.c in Sources */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000014 /* app_include.c */; };
                        1E2A00000000000000000004 /* LaunchScreen.storyboard in Resources */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000015 /* LaunchScreen.storyboard */; };
                        1E2A00000000000000000006 /* assets in Resources */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000017 /* assets */; };
@NATIVE_SOURCE_BUILD_FILES@
@FRAMEWORK_BUILD_FILES@
                /* End PBXBuildFile section */

                /* Begin PBXFileReference section */
                        1E2A00000000000000000010 /* @XCODE_NAME@.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = @XCODE_NAME@.app; sourceTree = BUILT_PRODUCTS_DIR; };
                        1E2A00000000000000000011 /* LibfdxIOSCApp.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = LibfdxIOSCApp.swift; sourceTree = "<group>"; };
                        1E2A00000000000000000012 /* TeaVMViewController.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = TeaVMViewController.swift; sourceTree = "<group>"; };
                        1E2A00000000000000000013 /* LibfdxIOSC-Bridging-Header.h */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.h; path = "LibfdxIOSC-Bridging-Header.h"; sourceTree = "<group>"; };
                        1E2A00000000000000000014 /* app_include.c */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.c.c; name = app_include.c; path = "../c/src/app_include.c"; sourceTree = SOURCE_ROOT; };
                        1E2A00000000000000000015 /* LaunchScreen.storyboard */ = {isa = PBXFileReference; lastKnownFileType = file.storyboard; path = LaunchScreen.storyboard; sourceTree = "<group>"; };
                        1E2A00000000000000000017 /* assets */ = {isa = PBXFileReference; lastKnownFileType = folder; name = assets; path = "../c/release/assets"; sourceTree = SOURCE_ROOT; };
                        1E2A00000000000000000019 /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };
@NATIVE_SOURCE_FILE_REFERENCES@
@FRAMEWORK_FILE_REFERENCES@
                /* End PBXFileReference section */

@COPY_FILES_BUILD_PHASE@

                /* Begin PBXFrameworksBuildPhase section */
                        1E2A00000000000000000020 /* Frameworks */ = {
                            isa = PBXFrameworksBuildPhase;
                            buildActionMask = 2147483647;
                            files = (
@FRAMEWORK_FILE_REFS@
                            );
                            runOnlyForDeploymentPostprocessing = 0;
                        };
                /* End PBXFrameworksBuildPhase section */

                /* Begin PBXGroup section */
                        1E2A00000000000000000030 = {
                            isa = PBXGroup;
                            children = (
                                1E2A00000000000000000031 /* Sources */,
                                1E2A00000000000000000032 /* Generated TeaVM C */,
                                1E2A00000000000000000034 /* Frameworks */,
                                1E2A00000000000000000033 /* Products */,
                            );
                            sourceTree = "<group>";
                        };
                        1E2A00000000000000000031 /* Sources */ = {
                            isa = PBXGroup;
                            children = (
                                1E2A00000000000000000011 /* LibfdxIOSCApp.swift */,
                                1E2A00000000000000000012 /* TeaVMViewController.swift */,
                                1E2A00000000000000000013 /* LibfdxIOSC-Bridging-Header.h */,
                                1E2A00000000000000000019 /* Info.plist */,
                                1E2A00000000000000000015 /* LaunchScreen.storyboard */,
                            );
                            path = Sources;
                            sourceTree = "<group>";
                        };
                        1E2A00000000000000000032 /* Generated TeaVM C */ = {
                            isa = PBXGroup;
                            children = (
                                1E2A00000000000000000014 /* app_include.c */,
@NATIVE_SOURCE_GROUP_REFS@
                            );
                            name = "Generated TeaVM C";
                            sourceTree = "<group>";
                        };
                        1E2A00000000000000000033 /* Products */ = {
                            isa = PBXGroup;
                            children = (
                                1E2A00000000000000000010 /* @XCODE_NAME@.app */,
                            );
                            name = Products;
                            sourceTree = "<group>";
                        };
                        1E2A00000000000000000034 /* Frameworks */ = {
                            isa = PBXGroup;
                            children = (
@FRAMEWORK_GROUP_REFS@
                            );
                            name = Frameworks;
                            sourceTree = "<group>";
                        };
                /* End PBXGroup section */

                /* Begin PBXNativeTarget section */
                        1E2A00000000000000000040 /* @XCODE_NAME@ */ = {
                            isa = PBXNativeTarget;
                            buildConfigurationList = 1E2A00000000000000000041 /* Build configuration list for PBXNativeTarget "@XCODE_NAME@" */;
                            buildPhases = (
                                1E2A00000000000000000050 /* Sources */,
                                1E2A00000000000000000020 /* Frameworks */,
                                1E2A00000000000000000051 /* Resources */,
@COPY_FILES_BUILD_PHASE_REF@
                            );
                            buildRules = (
                            );
                            dependencies = (
                            );
                            name = @XCODE_NAME@;
                            productName = @XCODE_NAME@;
                            productReference = 1E2A00000000000000000010 /* @XCODE_NAME@.app */;
                            productType = "com.apple.product-type.application";
                        };
                /* End PBXNativeTarget section */

                /* Begin PBXProject section */
                        1E2A00000000000000000060 /* Project object */ = {
                            isa = PBXProject;
                            attributes = {
                                BuildIndependentTargetsInParallel = 1;
                                LastSwiftUpdateCheck = 1540;
                                LastUpgradeCheck = 1540;
                                TargetAttributes = {
                                    1E2A00000000000000000040 = {
                                        CreatedOnToolsVersion = 15.4;
                                    };
                                };
                            };
                            buildConfigurationList = 1E2A00000000000000000061 /* Build configuration list for PBXProject "@XCODE_NAME@" */;
                            compatibilityVersion = "Xcode 14.0";
                            developmentRegion = en;
                            hasScannedForEncodings = 0;
                            knownRegions = (
                                en,
                                Base,
                            );
                            mainGroup = 1E2A00000000000000000030;
                            productRefGroup = 1E2A00000000000000000033 /* Products */;
                            projectDirPath = "";
                            projectRoot = "";
                            targets = (
                                1E2A00000000000000000040 /* @XCODE_NAME@ */,
                            );
                        };
                /* End PBXProject section */

                /* Begin PBXResourcesBuildPhase section */
                        1E2A00000000000000000051 /* Resources */ = {
                            isa = PBXResourcesBuildPhase;
                            buildActionMask = 2147483647;
                            files = (
                                1E2A00000000000000000004 /* LaunchScreen.storyboard in Resources */,
                                1E2A00000000000000000006 /* assets in Resources */,
                            );
                            runOnlyForDeploymentPostprocessing = 0;
                        };
                /* End PBXResourcesBuildPhase section */

                /* Begin PBXSourcesBuildPhase section */
                        1E2A00000000000000000050 /* Sources */ = {
                            isa = PBXSourcesBuildPhase;
                            buildActionMask = 2147483647;
                            files = (
                                1E2A00000000000000000001 /* LibfdxIOSCApp.swift in Sources */,
                                1E2A00000000000000000002 /* TeaVMViewController.swift in Sources */,
                                1E2A00000000000000000003 /* app_include.c in Sources */,
@NATIVE_SOURCE_FILE_REFS@
                            );
                            runOnlyForDeploymentPostprocessing = 0;
                        };
                /* End PBXSourcesBuildPhase section */

                /* Begin XCBuildConfiguration section */
                        1E2A00000000000000000070 /* Debug */ = {
                            isa = XCBuildConfiguration;
                            buildSettings = {
                                IPHONEOS_DEPLOYMENT_TARGET = 14.0;
                                SDKROOT = iphoneos;
                                SWIFT_OPTIMIZATION_LEVEL = "-Onone";
                            };
                            name = Debug;
                        };
                        1E2A00000000000000000071 /* Release */ = {
                            isa = XCBuildConfiguration;
                            buildSettings = {
                                IPHONEOS_DEPLOYMENT_TARGET = 14.0;
                                SDKROOT = iphoneos;
                                SWIFT_COMPILATION_MODE = wholemodule;
                            };
                            name = Release;
                        };
                        1E2A00000000000000000072 /* Debug */ = {
                            isa = XCBuildConfiguration;
                            buildSettings = {
                                CODE_SIGN_STYLE = Automatic;
                                CURRENT_PROJECT_VERSION = 1;
                                DEVELOPMENT_TEAM = "";
@FRAMEWORK_SEARCH_PATHS@
                                GENERATE_INFOPLIST_FILE = NO;
                                GCC_C_LANGUAGE_STANDARD = gnu11;
@METAL_BUILD_SETTINGS@
                                GCC_WARN_INHIBIT_ALL_WARNINGS = YES;
                                HEADER_SEARCH_PATHS = (
                                    "$(inherited)",
                                "$(PROJECT_DIR)/../c/external_cpp",
                                    "$(PROJECT_DIR)/../c/external_cpp/native_optimizations",
                                    "$(PROJECT_DIR)/../c/external_cpp/teavm_optimizations/teavm",
                                    "$(PROJECT_DIR)/../c/external_cpp/teavm_stats",
                                    "$(PROJECT_DIR)/../c/external_cpp/stb/include",
                                    "$(PROJECT_DIR)/../c/src",
                                );
                                INFOPLIST_FILE = Sources/Info.plist;
                                IPHONEOS_DEPLOYMENT_TARGET = 14.0;
                                LD_RUNPATH_SEARCH_PATHS = (
                                    "$(inherited)",
                                    "@executable_path/Frameworks",
                                );
                                MARKETING_VERSION = 1.0;
                                OTHER_CFLAGS = (
                                    "$(inherited)",
                                    "-Wno-incompatible-pointer-types-discards-qualifiers",
                                    "-Wno-parentheses-equality",
                                    "-Wno-pointer-sign",
                                    "-Wno-unused-value",
                                );
                                PRODUCT_BUNDLE_IDENTIFIER = @BUNDLE_IDENTIFIER@;
                                PRODUCT_NAME = "$(TARGET_NAME)";
                                SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
                                SUPPORTS_MACCATALYST = NO;
                                SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD = NO;
                                SWIFT_OBJC_BRIDGING_HEADER = "Sources/LibfdxIOSC-Bridging-Header.h";
                                SWIFT_VERSION = 5.0;
                                TARGETED_DEVICE_FAMILY = "1,2";
                            };
                            name = Debug;
                        };
                        1E2A00000000000000000073 /* Release */ = {
                            isa = XCBuildConfiguration;
                            buildSettings = {
                                CODE_SIGN_STYLE = Automatic;
                                CURRENT_PROJECT_VERSION = 1;
                                DEVELOPMENT_TEAM = "";
@FRAMEWORK_SEARCH_PATHS@
                                GENERATE_INFOPLIST_FILE = NO;
                                GCC_C_LANGUAGE_STANDARD = gnu11;
@METAL_BUILD_SETTINGS@
                                GCC_WARN_INHIBIT_ALL_WARNINGS = YES;
                                HEADER_SEARCH_PATHS = (
                                    "$(inherited)",
                                "$(PROJECT_DIR)/../c/external_cpp",
                                    "$(PROJECT_DIR)/../c/external_cpp/native_optimizations",
                                    "$(PROJECT_DIR)/../c/external_cpp/teavm_optimizations/teavm",
                                    "$(PROJECT_DIR)/../c/external_cpp/teavm_stats",
                                    "$(PROJECT_DIR)/../c/external_cpp/stb/include",
                                    "$(PROJECT_DIR)/../c/src",
                                );
                                INFOPLIST_FILE = Sources/Info.plist;
                                IPHONEOS_DEPLOYMENT_TARGET = 14.0;
                                LD_RUNPATH_SEARCH_PATHS = (
                                    "$(inherited)",
                                    "@executable_path/Frameworks",
                                );
                                MARKETING_VERSION = 1.0;
                                OTHER_CFLAGS = (
                                    "$(inherited)",
                                    "-Wno-incompatible-pointer-types-discards-qualifiers",
                                    "-Wno-parentheses-equality",
                                    "-Wno-pointer-sign",
                                    "-Wno-unused-value",
                                );
                                PRODUCT_BUNDLE_IDENTIFIER = @BUNDLE_IDENTIFIER@;
                                PRODUCT_NAME = "$(TARGET_NAME)";
                                SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
                                SUPPORTS_MACCATALYST = NO;
                                SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD = NO;
                                SWIFT_OBJC_BRIDGING_HEADER = "Sources/LibfdxIOSC-Bridging-Header.h";
                                SWIFT_VERSION = 5.0;
                                TARGETED_DEVICE_FAMILY = "1,2";
                            };
                            name = Release;
                        };
                /* End XCBuildConfiguration section */

                /* Begin XCConfigurationList section */
                        1E2A00000000000000000041 /* Build configuration list for PBXNativeTarget "@XCODE_NAME@" */ = {
                            isa = XCConfigurationList;
                            buildConfigurations = (
                                1E2A00000000000000000072 /* Debug */,
                                1E2A00000000000000000073 /* Release */,
                            );
                            defaultConfigurationIsVisible = 0;
                            defaultConfigurationName = Release;
                        };
                        1E2A00000000000000000061 /* Build configuration list for PBXProject "@XCODE_NAME@" */ = {
                            isa = XCConfigurationList;
                            buildConfigurations = (
                                1E2A00000000000000000070 /* Debug */,
                                1E2A00000000000000000071 /* Release */,
                            );
                            defaultConfigurationIsVisible = 0;
                            defaultConfigurationName = Release;
                        };
                /* End XCConfigurationList section */
                    };
                    rootObject = 1E2A00000000000000000060 /* Project object */;
                }
                """
                .replace("@XCODE_NAME@", xcodeName)
                .replace("@BUNDLE_IDENTIFIER@", bundleIdentifier)
                .replace("@NATIVE_SOURCE_BUILD_FILES@", nativeSourceBuildFiles(graphicsApi))
                .replace("@NATIVE_SOURCE_FILE_REFERENCES@", nativeSourceFileReferences(graphicsApi))
                .replace("@NATIVE_SOURCE_GROUP_REFS@", nativeSourceGroupRefs(graphicsApi))
                .replace("@NATIVE_SOURCE_FILE_REFS@", nativeSourceFileRefs(graphicsApi))
                .replace("@FRAMEWORK_BUILD_FILES@", frameworkBuildFiles(graphicsApi))
                .replace("@FRAMEWORK_FILE_REFERENCES@", frameworkFileReferences(graphicsApi))
                .replace("@COPY_FILES_BUILD_PHASE@", copyFilesBuildPhase(graphicsApi))
                .replace("@FRAMEWORK_FILE_REFS@", frameworkFileRefs(graphicsApi))
                .replace("@FRAMEWORK_GROUP_REFS@", frameworkGroupRefs(graphicsApi))
                .replace("@COPY_FILES_BUILD_PHASE_REF@", copyFilesBuildPhaseRef(graphicsApi))
                .replace("@FRAMEWORK_SEARCH_PATHS@", frameworkSearchPaths(graphicsApi))
                .replace("@METAL_BUILD_SETTINGS@", metalBuildSettings(graphicsApi));
    }

    private static String nativeSourceBuildFiles(IosCGraphicsApi graphicsApi) {
        if (!graphicsApi.isMetal()) {
            return "";
        }
        return "\t\t1E2A00000000000000000009 /* libfdx_ios_metal.mm in Sources */ = {isa = PBXBuildFile; "
                + "fileRef = 1E2A0000000000000000001B /* libfdx_ios_metal.mm */; "
                + "settings = {COMPILER_FLAGS = \"-fobjc-arc\";}; };\n";
    }

    private static String nativeSourceFileReferences(IosCGraphicsApi graphicsApi) {
        if (!graphicsApi.isMetal()) {
            return "";
        }
        return "\t\t1E2A0000000000000000001B /* libfdx_ios_metal.mm */ = {isa = PBXFileReference; "
                + "lastKnownFileType = sourcecode.cpp.objcpp; name = libfdx_ios_metal.mm; "
                + "path = \"../c/external_cpp/libfdx_ios_metal.mm\"; sourceTree = SOURCE_ROOT; };\n";
    }

    private static String nativeSourceGroupRefs(IosCGraphicsApi graphicsApi) {
        if (!graphicsApi.isMetal()) {
            return "";
        }
        return "\t\t\t\t1E2A0000000000000000001B /* libfdx_ios_metal.mm */,\n";
    }

    private static String nativeSourceFileRefs(IosCGraphicsApi graphicsApi) {
        if (!graphicsApi.isMetal()) {
            return "";
        }
        return "\t\t\t\t1E2A00000000000000000009 /* libfdx_ios_metal.mm in Sources */,\n";
    }

    private static String frameworkBuildFiles(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return """
                            1E2A00000000000000000005 /* Metal.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000016 /* Metal.framework */; };
                            1E2A00000000000000000007 /* MetalKit.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000018 /* MetalKit.framework */; };
                            1E2A00000000000000000008 /* QuartzCore.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 1E2A0000000000000000001A /* QuartzCore.framework */; };
                    """;
        }
        return """
                        1E2A00000000000000000005 /* GLKit.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000016 /* GLKit.framework */; };
                        1E2A00000000000000000007 /* OpenGLES.framework in Frameworks */ = {isa = PBXBuildFile; fileRef = 1E2A00000000000000000018 /* OpenGLES.framework */; };
                """;
    }

    private static String frameworkFileReferences(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return """
                            1E2A00000000000000000016 /* Metal.framework */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = Metal.framework; path = System/Library/Frameworks/Metal.framework; sourceTree = SDKROOT; };
                            1E2A00000000000000000018 /* MetalKit.framework */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = MetalKit.framework; path = System/Library/Frameworks/MetalKit.framework; sourceTree = SDKROOT; };
                            1E2A0000000000000000001A /* QuartzCore.framework */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = QuartzCore.framework; path = System/Library/Frameworks/QuartzCore.framework; sourceTree = SDKROOT; };
                    """;
        }
        return """
                        1E2A00000000000000000016 /* GLKit.framework */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = GLKit.framework; path = System/Library/Frameworks/GLKit.framework; sourceTree = SDKROOT; };
                        1E2A00000000000000000018 /* OpenGLES.framework */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = OpenGLES.framework; path = System/Library/Frameworks/OpenGLES.framework; sourceTree = SDKROOT; };
                """;
    }

    private static String copyFilesBuildPhase(IosCGraphicsApi graphicsApi) {
        return "";
    }

    private static String frameworkFileRefs(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return """
                                1E2A00000000000000000005 /* Metal.framework in Frameworks */,
                                1E2A00000000000000000007 /* MetalKit.framework in Frameworks */,
                                1E2A00000000000000000008 /* QuartzCore.framework in Frameworks */,
                    """;
        }
        return """
                                1E2A00000000000000000005 /* GLKit.framework in Frameworks */,
                                1E2A00000000000000000007 /* OpenGLES.framework in Frameworks */,
                """;
    }

    private static String frameworkGroupRefs(IosCGraphicsApi graphicsApi) {
        if (graphicsApi.isMetal()) {
            return """
                                1E2A00000000000000000016 /* Metal.framework */,
                                1E2A00000000000000000018 /* MetalKit.framework */,
                                1E2A0000000000000000001A /* QuartzCore.framework */,
                    """;
        }
        return """
                                1E2A00000000000000000016 /* GLKit.framework */,
                                1E2A00000000000000000018 /* OpenGLES.framework */,
                """;
    }

    private static String copyFilesBuildPhaseRef(IosCGraphicsApi graphicsApi) {
        return "";
    }

    private static String frameworkSearchPaths(IosCGraphicsApi graphicsApi) {
        return "";
    }

    private static String metalBuildSettings(IosCGraphicsApi graphicsApi) {
        if (!graphicsApi.isMetal()) {
            return "";
        }
        return """
                                CLANG_CXX_LANGUAGE_STANDARD = "gnu++17";
                                CLANG_CXX_LIBRARY = "libc++";
                                CLANG_ENABLE_OBJC_ARC = YES;
                """;
    }

    private static void copyNativeResources(Iterable<Path> nativeResourceClasspath, Path outputRoot)
            throws IOException {
        deleteDirectory(outputRoot);
        Files.createDirectories(outputRoot);
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        for (Path entry : nativeResourceClasspath) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                copyNativeResourcesFromDirectory(normalized, normalizedOutputRoot);
            } else if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".jar")) {
                copyNativeResourcesFromJar(normalized, normalizedOutputRoot);
            }
        }
    }

    private static Set<Path> copyAssets(Iterable<Path> assets, Path outputRoot) throws IOException {
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        deleteDirectory(outputRoot);
        Files.createDirectories(outputRoot);
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        for (Path asset : assets) {
            Path normalizedAsset = asset.toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedAsset)) {
                written.addAll(copyDirectory(normalizedAsset, normalizedOutputRoot));
            } else if (Files.isRegularFile(normalizedAsset)) {
                Path output = normalizedOutputRoot.resolve(normalizedAsset.getFileName()).normalize();
                if (!output.startsWith(normalizedOutputRoot)) {
                    throw new IOException("Refusing to copy asset outside output directory: " + normalizedAsset);
                }
                Files.createDirectories(output.getParent());
                Files.copy(normalizedAsset, output, StandardCopyOption.REPLACE_EXISTING);
                written.add(output.toAbsolutePath().normalize());
            }
        }
        return Set.copyOf(written);
    }

    private static void copyNativeResourcesFromDirectory(Path classpathRoot, Path outputRoot) throws IOException {
        for (String prefix : NATIVE_RESOURCE_PREFIXES) {
            Path nativeRoot = classpathRoot.resolve(prefix).normalize();
            if (Files.isDirectory(nativeRoot)) {
                copyDirectory(nativeRoot, outputRoot);
            }
        }
    }

    private static Set<Path> copyDirectory(Path sourceRoot, Path outputRoot) throws IOException {
        LinkedHashSet<Path> written = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                Path relative = sourceRoot.relativize(source);
                Path output = outputRoot.resolve(relative).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Refusing to copy resource outside output directory: " + source);
                }
                Files.createDirectories(output.getParent());
                Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
                written.add(output.toAbsolutePath().normalize());
            }
        }
        return Set.copyOf(written);
    }

    private static void copyNativeResourcesFromJar(Path jar, Path outputRoot) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                if (entry.isDirectory()) {
                    continue;
                }
                String relativePath = relativeNativeResourcePath(entry.getName());
                if (relativePath == null || relativePath.isBlank()) {
                    continue;
                }
                Path output = outputRoot.resolve(relativePath).normalize();
                if (!output.startsWith(outputRoot)) {
                    throw new IOException("Refusing to extract native resource outside output directory: "
                            + entry.getName());
                }
                Files.createDirectories(output.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String relativeNativeResourcePath(String entryName) {
        for (String prefix : NATIVE_RESOURCE_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return entryName.substring(prefix.length());
            }
        }
        return null;
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static String xcodeName(String value) {
        String sanitized = cmakeIdentifier(value);
        if (sanitized.isBlank()) {
            return "LibfdxIOSC";
        }
        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            return "Libfdx" + sanitized;
        }
        return sanitized;
    }

    private static String cmakeIdentifier(String value) {
        String text = value != null ? value : "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
    }

    private static String xml(String value) {
        String text = value != null ? value : "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
