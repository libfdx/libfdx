package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;

final class D3D12Ffm {
    static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.nativeOrder());
    static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT.withOrder(ByteOrder.nativeOrder());
    static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG.withOrder(ByteOrder.nativeOrder());
    static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.nativeOrder());
    static final AddressLayout ADDRESS = ValueLayout.ADDRESS;
    static final MemorySegment NULL = MemorySegment.NULL;

    static final int S_OK = 0;
    static final int DXGI_ERROR_NOT_FOUND = -2005270526;
    static final int WAIT_OBJECT_0 = 0;

    static final int DXGI_CREATE_FACTORY_DEBUG = 1;
    static final int DXGI_ADAPTER_FLAG_SOFTWARE = 2;
    static final int DXGI_USAGE_RENDER_TARGET_OUTPUT = 32;
    static final int DXGI_SCALING_STRETCH = 0;
    static final int DXGI_SWAP_EFFECT_FLIP_DISCARD = 4;
    static final int DXGI_ALPHA_MODE_UNSPECIFIED = 0;
    static final int DXGI_MWA_NO_ALT_ENTER = 2;

    static final int DXGI_FORMAT_UNKNOWN = 0;
    static final int DXGI_FORMAT_R32G32B32A32_FLOAT = 2;
    static final int DXGI_FORMAT_R32G32B32_FLOAT = 6;
    static final int DXGI_FORMAT_R32G32_FLOAT = 16;
    static final int DXGI_FORMAT_R8G8B8A8_UNORM = 28;
    static final int DXGI_FORMAT_R8G8B8A8_UNORM_SRGB = 29;
    static final int DXGI_FORMAT_D32_FLOAT = 40;
    static final int DXGI_FORMAT_R32_FLOAT = 41;
    static final int DXGI_FORMAT_R16_UINT = 57;
    static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
    static final int DXGI_FORMAT_B8G8R8A8_UNORM_SRGB = 91;

    static final int D3D_FEATURE_LEVEL_11_0 = 45056;
    static final int D3D_PRIMITIVE_TOPOLOGY_LINELIST = 2;
    static final int D3D_PRIMITIVE_TOPOLOGY_TRIANGLELIST = 4;
    static final int D3D_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP = 5;

    static final int D3D12_COMMAND_LIST_TYPE_DIRECT = 0;
    static final int D3D12_COMMAND_QUEUE_PRIORITY_NORMAL = 0;
    static final int D3D12_COMMAND_QUEUE_FLAG_NONE = 0;
    static final int D3D12_DESCRIPTOR_HEAP_TYPE_CBV_SRV_UAV = 0;
    static final int D3D12_DESCRIPTOR_HEAP_TYPE_SAMPLER = 1;
    static final int D3D12_DESCRIPTOR_HEAP_TYPE_RTV = 2;
    static final int D3D12_DESCRIPTOR_HEAP_TYPE_DSV = 3;
    static final int D3D12_DESCRIPTOR_HEAP_FLAG_NONE = 0;
    static final int D3D12_DESCRIPTOR_HEAP_FLAG_SHADER_VISIBLE = 1;
    static final int D3D12_HEAP_TYPE_DEFAULT = 1;
    static final int D3D12_HEAP_TYPE_UPLOAD = 2;
    static final int D3D12_HEAP_TYPE_READBACK = 3;
    static final int D3D12_CPU_PAGE_PROPERTY_UNKNOWN = 0;
    static final int D3D12_MEMORY_POOL_UNKNOWN = 0;
    static final int D3D12_HEAP_FLAG_NONE = 0;
    static final int D3D12_RESOURCE_DIMENSION_BUFFER = 1;
    static final int D3D12_RESOURCE_DIMENSION_TEXTURE2D = 3;
    static final int D3D12_TEXTURE_LAYOUT_UNKNOWN = 0;
    static final int D3D12_TEXTURE_LAYOUT_ROW_MAJOR = 1;
    static final int D3D12_RESOURCE_FLAG_NONE = 0;
    static final int D3D12_RESOURCE_FLAG_ALLOW_RENDER_TARGET = 1;
    static final int D3D12_RESOURCE_FLAG_ALLOW_DEPTH_STENCIL = 2;
    static final int D3D12_RESOURCE_STATE_PRESENT = 0;
    static final int D3D12_RESOURCE_STATE_RENDER_TARGET = 4;
    static final int D3D12_RESOURCE_STATE_DEPTH_WRITE = 16;
    static final int D3D12_RESOURCE_STATE_PIXEL_SHADER_RESOURCE = 128;
    static final int D3D12_RESOURCE_STATE_COPY_DEST = 1024;
    static final int D3D12_RESOURCE_STATE_COPY_SOURCE = 2048;
    static final int D3D12_RESOURCE_STATE_GENERIC_READ = 2755;
    static final int D3D12_FENCE_FLAG_NONE = 0;
    static final int D3D12_DSV_DIMENSION_TEXTURE2D = 3;
    static final int D3D12_RESOURCE_BARRIER_TYPE_TRANSITION = 0;
    static final int D3D12_RESOURCE_BARRIER_FLAG_NONE = 0;
    static final int D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES = -1;
    static final int D3D12_CLEAR_FLAG_DEPTH = 1;
    static final int D3D12_CONSTANT_BUFFER_DATA_PLACEMENT_ALIGNMENT = 256;
    static final int D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX = 0;
    static final int D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT = 1;

    static final int D3D12_PRIMITIVE_TOPOLOGY_TYPE_LINE = 2;
    static final int D3D12_PRIMITIVE_TOPOLOGY_TYPE_TRIANGLE = 3;
    static final int D3D12_TEXTURE_ADDRESS_MODE_WRAP = 1;
    static final int D3D12_TEXTURE_ADDRESS_MODE_MIRROR = 2;
    static final int D3D12_TEXTURE_ADDRESS_MODE_CLAMP = 3;
    static final int D3D12_FILTER_MIN_MAG_MIP_POINT = 0;
    static final int D3D12_FILTER_MIN_MAG_MIP_LINEAR = 21;
    static final int D3D12_COMPARISON_FUNC_LESS_EQUAL = 4;
    static final int D3D12_COMPARISON_FUNC_ALWAYS = 8;
    static final int D3D12_SRV_DIMENSION_TEXTURE2D = 4;
    static final int D3D12_DEFAULT_SHADER_4_COMPONENT_MAPPING = 5768;
    static final int D3D12_DESCRIPTOR_RANGE_TYPE_SRV = 0;
    static final int D3D12_DESCRIPTOR_RANGE_TYPE_SAMPLER = 3;
    static final int D3D12_ROOT_PARAMETER_TYPE_DESCRIPTOR_TABLE = 0;
    static final int D3D12_ROOT_PARAMETER_TYPE_CBV = 2;
    static final int D3D12_SHADER_VISIBILITY_ALL = 0;
    static final int D3D12_ROOT_SIGNATURE_FLAG_ALLOW_INPUT_ASSEMBLER_INPUT_LAYOUT = 1;
    static final int D3D_ROOT_SIGNATURE_VERSION_1 = 1;
    static final int D3D12_INPUT_CLASSIFICATION_PER_VERTEX_DATA = 0;
    static final int D3D12_INPUT_CLASSIFICATION_PER_INSTANCE_DATA = 1;
    static final int D3D12_BLEND_ONE = 2;
    static final int D3D12_BLEND_SRC_ALPHA = 5;
    static final int D3D12_BLEND_INV_SRC_ALPHA = 6;
    static final int D3D12_BLEND_OP_ADD = 1;
    static final int D3D12_LOGIC_OP_NOOP = 5;
    static final byte D3D12_COLOR_WRITE_ENABLE_ALL = 15;
    static final int D3D12_FILL_MODE_SOLID = 3;
    static final int D3D12_CULL_MODE_NONE = 1;
    static final int D3D12_DEPTH_WRITE_MASK_ZERO = 0;
    static final int D3D12_DEPTH_WRITE_MASK_ALL = 1;
    static final byte D3D12_DEFAULT_STENCIL_READ_MASK = (byte)0xff;
    static final byte D3D12_DEFAULT_STENCIL_WRITE_MASK = (byte)0xff;
    static final int D3D12_PIPELINE_STATE_FLAG_NONE = 0;

    static final int D3DCOMPILE_DEBUG = 1;
    static final int D3DCOMPILE_SKIP_OPTIMIZATION = 4;
    static final int D3DCOMPILE_ENABLE_STRICTNESS = 2048;
    static final int D3DCOMPILE_OPTIMIZATION_LEVEL3 = 32768;

    static final int SIZE_GUID = 16;
    static final int SIZE_DXGI_ADAPTER_DESC1 = 312;
    static final int OFF_DXGI_ADAPTER_FLAGS = 304;
    static final int SIZE_COMMAND_QUEUE_DESC = 16;
    static final int SIZE_DESCRIPTOR_HEAP_DESC = 16;
    static final int SIZE_HEAP_PROPERTIES = 20;
    static final int SIZE_RESOURCE_DESC = 56;
    static final int SIZE_RANGE = 16;
    static final int SIZE_SWAP_CHAIN_DESC1 = 48;
    static final int SIZE_CLEAR_VALUE = 20;
    static final int SIZE_DSV_DESC = 24;
    static final int SIZE_RESOURCE_BARRIER = 32;
    static final int SIZE_VIEWPORT = 24;
    static final int SIZE_RECT = 16;
    static final int SIZE_VERTEX_BUFFER_VIEW = 16;
    static final int SIZE_INDEX_BUFFER_VIEW = 16;
    static final int SIZE_PLACED_FOOTPRINT = 32;
    static final int SIZE_TEXTURE_COPY_LOCATION = 48;
    static final int SIZE_SRV_DESC = 40;
    static final int SIZE_SAMPLER_DESC = 52;
    static final int SIZE_DESCRIPTOR_RANGE = 20;
    static final int SIZE_ROOT_PARAMETER = 32;
    static final int SIZE_ROOT_SIGNATURE_DESC = 40;
    static final int SIZE_INPUT_ELEMENT_DESC = 32;
    static final int SIZE_GRAPHICS_PIPELINE_DESC = 656;

    static final int OFF_RESOURCE_DESC_ALIGNMENT = 8;
    static final int OFF_RESOURCE_DESC_WIDTH = 16;
    static final int OFF_RESOURCE_DESC_HEIGHT = 24;
    static final int OFF_RESOURCE_DESC_DEPTH_OR_ARRAY = 28;
    static final int OFF_RESOURCE_DESC_MIP_LEVELS = 30;
    static final int OFF_RESOURCE_DESC_FORMAT = 32;
    static final int OFF_RESOURCE_DESC_SAMPLE_COUNT = 36;
    static final int OFF_RESOURCE_DESC_SAMPLE_QUALITY = 40;
    static final int OFF_RESOURCE_DESC_LAYOUT = 44;
    static final int OFF_RESOURCE_DESC_FLAGS = 48;
    static final int OFF_SWAP_SAMPLE_COUNT = 16;
    static final int OFF_SWAP_BUFFER_USAGE = 24;
    static final int OFF_SWAP_BUFFER_COUNT = 28;
    static final int OFF_SWAP_SCALING = 32;
    static final int OFF_SWAP_EFFECT = 36;
    static final int OFF_SWAP_ALPHA_MODE = 40;
    static final int OFF_SWAP_FLAGS = 44;
    static final int OFF_BARRIER_RESOURCE = 8;
    static final int OFF_BARRIER_SUBRESOURCE = 16;
    static final int OFF_BARRIER_STATE_BEFORE = 20;
    static final int OFF_BARRIER_STATE_AFTER = 24;
    static final int OFF_FOOTPRINT_OFFSET = 0;
    static final int OFF_FOOTPRINT_FORMAT = 8;
    static final int OFF_FOOTPRINT_WIDTH = 12;
    static final int OFF_FOOTPRINT_HEIGHT = 16;
    static final int OFF_FOOTPRINT_DEPTH = 20;
    static final int OFF_FOOTPRINT_ROW_PITCH = 24;
    static final int OFF_COPY_LOCATION_TYPE = 8;
    static final int OFF_COPY_LOCATION_UNION = 16;
    static final int OFF_SRV_COMPONENT_MAPPING = 8;
    static final int OFF_SRV_TEXTURE2D = 16;
    static final int OFF_ROOT_PARAMETER_UNION = 8;
    static final int OFF_ROOT_PARAMETER_VISIBILITY = 24;
    static final int OFF_ROOT_SIGNATURE_PARAMETERS = 8;
    static final int OFF_ROOT_SIGNATURE_STATIC_SAMPLERS = 24;
    static final int OFF_ROOT_SIGNATURE_FLAGS = 32;
    static final int OFF_PIPELINE_VS = 8;
    static final int OFF_PIPELINE_PS = 24;
    static final int OFF_PIPELINE_BLEND = 120;
    static final int OFF_PIPELINE_SAMPLE_MASK = 448;
    static final int OFF_PIPELINE_RASTERIZER = 452;
    static final int OFF_PIPELINE_DEPTH_STENCIL = 496;
    static final int OFF_PIPELINE_INPUT_LAYOUT = 552;
    static final int OFF_PIPELINE_PRIMITIVE_TOPOLOGY = 572;
    static final int OFF_PIPELINE_NUM_RENDER_TARGETS = 576;
    static final int OFF_PIPELINE_RTV_FORMATS = 580;
    static final int OFF_PIPELINE_DSV_FORMAT = 612;
    static final int OFF_PIPELINE_SAMPLE_DESC = 616;
    static final int OFF_PIPELINE_FLAGS = 648;

    static final int SLOT_RELEASE = 2;
    static final int SLOT_DEBUG_ENABLE = 3;
    static final int SLOT_FACTORY_MAKE_WINDOW_ASSOCIATION = 8;
    static final int SLOT_FACTORY_ENUM_ADAPTERS1 = 12;
    static final int SLOT_FACTORY_CREATE_SWAP_CHAIN_FOR_HWND = 15;
    static final int SLOT_ADAPTER_GET_DESC1 = 10;
    static final int SLOT_SWAP_PRESENT = 8;
    static final int SLOT_SWAP_GET_BUFFER = 9;
    static final int SLOT_SWAP_RESIZE_BUFFERS = 13;
    static final int SLOT_SWAP_GET_CURRENT_BACK_BUFFER_INDEX = 36;
    static final int SLOT_DEVICE_CREATE_COMMAND_QUEUE = 8;
    static final int SLOT_DEVICE_CREATE_COMMAND_ALLOCATOR = 9;
    static final int SLOT_DEVICE_CREATE_GRAPHICS_PIPELINE_STATE = 10;
    static final int SLOT_DEVICE_CREATE_COMMAND_LIST = 12;
    static final int SLOT_DEVICE_CREATE_DESCRIPTOR_HEAP = 14;
    static final int SLOT_DEVICE_GET_DESCRIPTOR_INCREMENT = 15;
    static final int SLOT_DEVICE_CREATE_ROOT_SIGNATURE = 16;
    static final int SLOT_DEVICE_CREATE_SHADER_RESOURCE_VIEW = 18;
    static final int SLOT_DEVICE_CREATE_RENDER_TARGET_VIEW = 20;
    static final int SLOT_DEVICE_CREATE_DEPTH_STENCIL_VIEW = 21;
    static final int SLOT_DEVICE_CREATE_SAMPLER = 22;
    static final int SLOT_DEVICE_COPY_DESCRIPTORS_SIMPLE = 24;
    static final int SLOT_DEVICE_CREATE_COMMITTED_RESOURCE = 27;
    static final int SLOT_DEVICE_CREATE_FENCE = 36;
    static final int SLOT_DEVICE_GET_COPYABLE_FOOTPRINTS = 38;
    static final int SLOT_ALLOCATOR_RESET = 8;
    static final int SLOT_QUEUE_EXECUTE_COMMAND_LISTS = 10;
    static final int SLOT_QUEUE_SIGNAL = 14;
    static final int SLOT_COMMANDS_CLOSE = 9;
    static final int SLOT_COMMANDS_RESET = 10;
    static final int SLOT_COMMANDS_DRAW_INSTANCED = 12;
    static final int SLOT_COMMANDS_DRAW_INDEXED_INSTANCED = 13;
    static final int SLOT_COMMANDS_COPY_TEXTURE_REGION = 16;
    static final int SLOT_COMMANDS_IA_SET_PRIMITIVE_TOPOLOGY = 20;
    static final int SLOT_COMMANDS_RS_SET_VIEWPORTS = 21;
    static final int SLOT_COMMANDS_RS_SET_SCISSORS = 22;
    static final int SLOT_COMMANDS_SET_PIPELINE_STATE = 25;
    static final int SLOT_COMMANDS_RESOURCE_BARRIER = 26;
    static final int SLOT_COMMANDS_SET_DESCRIPTOR_HEAPS = 28;
    static final int SLOT_COMMANDS_SET_GRAPHICS_ROOT_SIGNATURE = 30;
    static final int SLOT_COMMANDS_SET_GRAPHICS_ROOT_DESCRIPTOR_TABLE = 32;
    static final int SLOT_COMMANDS_SET_GRAPHICS_ROOT_CBV = 38;
    static final int SLOT_COMMANDS_IA_SET_INDEX_BUFFER = 43;
    static final int SLOT_COMMANDS_IA_SET_VERTEX_BUFFERS = 44;
    static final int SLOT_COMMANDS_OM_SET_RENDER_TARGETS = 46;
    static final int SLOT_COMMANDS_CLEAR_DEPTH = 47;
    static final int SLOT_COMMANDS_CLEAR_RENDER_TARGET = 48;
    static final int SLOT_FENCE_GET_COMPLETED_VALUE = 8;
    static final int SLOT_FENCE_SET_EVENT_ON_COMPLETION = 9;
    static final int SLOT_RESOURCE_MAP = 8;
    static final int SLOT_RESOURCE_UNMAP = 9;
    static final int SLOT_RESOURCE_GET_DESC = 10;
    static final int SLOT_RESOURCE_GET_GPU_VIRTUAL_ADDRESS = 11;
    static final int SLOT_HEAP_GET_CPU_HANDLE = 9;
    static final int SLOT_HEAP_GET_GPU_HANDLE = 10;
    static final int SLOT_BLOB_GET_BUFFER_POINTER = 3;
    static final int SLOT_BLOB_GET_BUFFER_SIZE = 4;

    private static final Arena GLOBAL = Arena.global();
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup D3D12;
    private static final SymbolLookup DXGI;
    private static final SymbolLookup COMPILER;
    private static final SymbolLookup KERNEL;

    static final MemorySegment IID_ID3D12_DEBUG;
    static final MemorySegment IID_IDXGI_FACTORY4;
    static final MemorySegment IID_IDXGI_ADAPTER1;
    static final MemorySegment IID_IDXGI_SWAP_CHAIN3;
    static final MemorySegment IID_ID3D12_DEVICE;
    static final MemorySegment IID_ID3D12_COMMAND_QUEUE;
    static final MemorySegment IID_ID3D12_COMMAND_ALLOCATOR;
    static final MemorySegment IID_ID3D12_GRAPHICS_COMMAND_LIST;
    static final MemorySegment IID_ID3D12_DESCRIPTOR_HEAP;
    static final MemorySegment IID_ID3D12_RESOURCE;
    static final MemorySegment IID_ID3D12_FENCE;
    static final MemorySegment IID_ID3D12_ROOT_SIGNATURE;
    static final MemorySegment IID_ID3D12_PIPELINE_STATE;

    private static final MethodHandle D3D12_CREATE_DEVICE;
    private static final MethodHandle D3D12_GET_DEBUG_INTERFACE;
    private static final MethodHandle D3D12_SERIALIZE_ROOT_SIGNATURE;
    private static final MethodHandle CREATE_DXGI_FACTORY2;
    private static final MethodHandle D3D_COMPILE;
    private static final MethodHandle CREATE_EVENT;
    private static final MethodHandle WAIT_FOR_SINGLE_OBJECT;
    private static final MethodHandle CLOSE_HANDLE;

    private static final MethodHandle I_A = down(FunctionDescriptor.of(INT, ADDRESS));
    private static final MethodHandle V_A = down(FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle I_AA = down(FunctionDescriptor.of(INT, ADDRESS, ADDRESS));
    private static final MethodHandle I_AAA = down(FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle I_AIA = down(FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS));
    private static final MethodHandle I_AAI = down(FunctionDescriptor.of(INT, ADDRESS, ADDRESS, INT));
    private static final MethodHandle I_AAAA = down(FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle I_AAAAAAA = down(FunctionDescriptor.of(INT,
            ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle I_AII = down(FunctionDescriptor.of(INT, ADDRESS, INT, INT));
    private static final MethodHandle I_AIAA = down(FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS, ADDRESS));
    private static final MethodHandle I_AIIIII = down(FunctionDescriptor.of(INT,
            ADDRESS, INT, INT, INT, INT, INT));
    private static final MethodHandle I_AIIAAAA = down(FunctionDescriptor.of(INT,
            ADDRESS, INT, INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle I_AI = down(FunctionDescriptor.of(INT, ADDRESS, INT));
    private static final MethodHandle I_AIALAA = down(FunctionDescriptor.of(INT,
            ADDRESS, INT, ADDRESS, LONG, ADDRESS, ADDRESS));
    private static final MethodHandle I_AAIAIAAA = down(FunctionDescriptor.of(INT,
            ADDRESS, ADDRESS, INT, ADDRESS, INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle I_ALIAA = down(FunctionDescriptor.of(INT,
            ADDRESS, LONG, INT, ADDRESS, ADDRESS));
    private static final MethodHandle I_AAL = down(FunctionDescriptor.of(INT, ADDRESS, ADDRESS, LONG));
    private static final MethodHandle V_AAAL = down(FunctionDescriptor.ofVoid(
            ADDRESS, ADDRESS, ADDRESS, LONG));
    private static final MethodHandle V_AAL = down(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, LONG));
    private static final MethodHandle V_AILLI = down(FunctionDescriptor.ofVoid(
            ADDRESS, INT, LONG, LONG, INT));
    private static final MethodHandle V_AAIILAAAA = down(FunctionDescriptor.ofVoid(
            ADDRESS, ADDRESS, INT, INT, LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle V_AIIII = down(FunctionDescriptor.ofVoid(
            ADDRESS, INT, INT, INT, INT));
    private static final MethodHandle V_AIIIII = down(FunctionDescriptor.ofVoid(
            ADDRESS, INT, INT, INT, INT, INT));
    private static final MethodHandle V_AAIIIAA = down(FunctionDescriptor.ofVoid(
            ADDRESS, ADDRESS, INT, INT, INT, ADDRESS, ADDRESS));
    private static final MethodHandle V_AI = down(FunctionDescriptor.ofVoid(ADDRESS, INT));
    private static final MethodHandle V_AIA = down(FunctionDescriptor.ofVoid(ADDRESS, INT, ADDRESS));
    private static final MethodHandle V_AA = down(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle V_AIL = down(FunctionDescriptor.ofVoid(ADDRESS, INT, LONG));
    private static final MethodHandle V_AIIA = down(FunctionDescriptor.ofVoid(ADDRESS, INT, INT, ADDRESS));
    private static final MethodHandle V_AIAIA = down(FunctionDescriptor.ofVoid(
            ADDRESS, INT, ADDRESS, INT, ADDRESS));
    private static final MethodHandle V_ALIFBIA = down(FunctionDescriptor.ofVoid(
            ADDRESS, LONG, INT, FLOAT, BYTE, INT, ADDRESS));
    private static final MethodHandle V_ALAIA = down(FunctionDescriptor.ofVoid(
            ADDRESS, LONG, ADDRESS, INT, ADDRESS));
    private static final MethodHandle L_A = down(FunctionDescriptor.of(LONG, ADDRESS));
    private static final MethodHandle I_ALA = down(FunctionDescriptor.of(INT, ADDRESS, LONG, ADDRESS));
    private static final MethodHandle I_AIAA_MAP = down(FunctionDescriptor.of(INT,
            ADDRESS, INT, ADDRESS, ADDRESS));
    private static final MethodHandle V_AIA_UNMAP = down(FunctionDescriptor.ofVoid(
            ADDRESS, INT, ADDRESS));
    private static final MethodHandle A_AA = down(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle A_A = down(FunctionDescriptor.of(ADDRESS, ADDRESS));

    static {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!os.startsWith("windows") || !(arch.equals("amd64") || arch.equals("x86_64"))) {
            throw new ExceptionInInitializerError("Direct3D 12 FFM requires Windows x64");
        }
        try {
            D3D12 = SymbolLookup.libraryLookup("d3d12.dll", GLOBAL);
            DXGI = SymbolLookup.libraryLookup("dxgi.dll", GLOBAL);
            COMPILER = SymbolLookup.libraryLookup("d3dcompiler_47.dll", GLOBAL);
            KERNEL = SymbolLookup.libraryLookup("kernel32.dll", GLOBAL);
            D3D12_CREATE_DEVICE = bound(D3D12, "D3D12CreateDevice",
                    FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS, ADDRESS));
            D3D12_GET_DEBUG_INTERFACE = bound(D3D12, "D3D12GetDebugInterface",
                    FunctionDescriptor.of(INT, ADDRESS, ADDRESS));
            D3D12_SERIALIZE_ROOT_SIGNATURE = bound(D3D12, "D3D12SerializeRootSignature",
                    FunctionDescriptor.of(INT, ADDRESS, INT, ADDRESS, ADDRESS));
            CREATE_DXGI_FACTORY2 = bound(DXGI, "CreateDXGIFactory2",
                    FunctionDescriptor.of(INT, INT, ADDRESS, ADDRESS));
            D3D_COMPILE = bound(COMPILER, "D3DCompile", FunctionDescriptor.of(INT,
                    ADDRESS, LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                    INT, INT, ADDRESS, ADDRESS));
            CREATE_EVENT = bound(KERNEL, "CreateEventW",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, INT, INT, ADDRESS));
            WAIT_FOR_SINGLE_OBJECT = bound(KERNEL, "WaitForSingleObject",
                    FunctionDescriptor.of(INT, ADDRESS, INT));
            CLOSE_HANDLE = bound(KERNEL, "CloseHandle", FunctionDescriptor.of(INT, ADDRESS));
        } catch (RuntimeException error) {
            throw new ExceptionInInitializerError(error);
        }

        IID_ID3D12_DEBUG = guid(0x344488b7, 0x6846, 0x474b,
                0xb9, 0x89, 0xf0, 0x27, 0x44, 0x82, 0x45, 0xe0);
        IID_IDXGI_FACTORY4 = guid(0x1bc6ea02, 0xef36, 0x464f,
                0xbf, 0x0c, 0x21, 0xca, 0x39, 0xe5, 0x16, 0x8a);
        IID_IDXGI_ADAPTER1 = guid(0x29038f61, 0x3839, 0x4626,
                0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05);
        IID_IDXGI_SWAP_CHAIN3 = guid(0x94d99bdb, 0xf1f8, 0x4ab0,
                0xb2, 0x36, 0x7d, 0xa0, 0x17, 0x0e, 0xda, 0xb1);
        IID_ID3D12_DEVICE = guid(0x189819f1, 0x1db6, 0x4b57,
                0xbe, 0x54, 0x18, 0x21, 0x33, 0x9b, 0x85, 0xf7);
        IID_ID3D12_COMMAND_QUEUE = guid(0x0ec870a6, 0x5d7e, 0x4c22,
                0x8c, 0xfc, 0x5b, 0xaa, 0xe0, 0x76, 0x16, 0xed);
        IID_ID3D12_COMMAND_ALLOCATOR = guid(0x6102dee4, 0xaf59, 0x4b09,
                0xb9, 0x99, 0xb4, 0x4d, 0x73, 0xf0, 0x9b, 0x24);
        IID_ID3D12_GRAPHICS_COMMAND_LIST = guid(0x5b160d0f, 0xac1b, 0x4185,
                0x8b, 0xa8, 0xb3, 0xae, 0x42, 0xa5, 0xa4, 0x55);
        IID_ID3D12_DESCRIPTOR_HEAP = guid(0x8efb471d, 0x616c, 0x4f49,
                0x90, 0xf7, 0x12, 0x7b, 0xb7, 0x63, 0xfa, 0x51);
        IID_ID3D12_RESOURCE = guid(0x696442be, 0xa72e, 0x4059,
                0xbc, 0x79, 0x5b, 0x5c, 0x98, 0x04, 0x0f, 0xad);
        IID_ID3D12_FENCE = guid(0x0a753dcf, 0xc4d8, 0x4b91,
                0xad, 0xf6, 0xbe, 0x5a, 0x60, 0xd9, 0x5a, 0x76);
        IID_ID3D12_ROOT_SIGNATURE = guid(0xc54a6b66, 0x72df, 0x4ee8,
                0x8b, 0xe5, 0xa9, 0x46, 0xa1, 0x42, 0x92, 0x14);
        IID_ID3D12_PIPELINE_STATE = guid(0x765a30f3, 0xf624, 0x4c6f,
                0xa8, 0x28, 0xac, 0xe9, 0x48, 0x62, 0x24, 0x45);
    }

    private D3D12Ffm() {
    }

    static int createDevice(MemorySegment adapter, MemorySegment output) {
        try {
            return (int)D3D12_CREATE_DEVICE.invokeExact(adapter, D3D_FEATURE_LEVEL_11_0,
                    IID_ID3D12_DEVICE, output);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int getDebugInterface(MemorySegment output) {
        try {
            return (int)D3D12_GET_DEBUG_INTERFACE.invokeExact(IID_ID3D12_DEBUG, output);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int serializeRootSignature(MemorySegment descriptor, MemorySegment output, MemorySegment errors) {
        try {
            return (int)D3D12_SERIALIZE_ROOT_SIGNATURE.invokeExact(
                    descriptor, D3D_ROOT_SIGNATURE_VERSION_1, output, errors);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int createFactory(int flags, MemorySegment output) {
        try {
            return (int)CREATE_DXGI_FACTORY2.invokeExact(flags, IID_IDXGI_FACTORY4, output);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int compile(MemorySegment source, long sourceSize, MemorySegment label,
            MemorySegment entryPoint, MemorySegment target, int flags,
            MemorySegment output, MemorySegment errors) {
        try {
            return (int)D3D_COMPILE.invokeExact(source, sourceSize, label, NULL, NULL,
                    entryPoint, target, flags, 0, output, errors);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static MemorySegment createEvent() {
        try {
            return (MemorySegment)CREATE_EVENT.invokeExact(NULL, 0, 0, NULL);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int waitForSingleObject(MemorySegment event, int milliseconds) {
        try {
            return (int)WAIT_FOR_SINGLE_OBJECT.invokeExact(event, milliseconds);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void closeHandle(MemorySegment handle) {
        if (isNull(handle)) {
            return;
        }
        try {
            int ignored = (int)CLOSE_HANDLE.invokeExact(handle);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntA(MemorySegment object, int slot) {
        try {
            return (int)I_A.invokeExact(function(object, slot), object);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidA(MemorySegment object, int slot) {
        try {
            V_A.invokeExact(function(object, slot), object);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAA(MemorySegment object, int slot, MemorySegment a) {
        try {
            return (int)I_AA.invokeExact(function(object, slot), object, a);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAA(MemorySegment object, int slot, MemorySegment a, MemorySegment b) {
        try {
            return (int)I_AAA.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAIA(MemorySegment object, int slot, int a, MemorySegment b) {
        try {
            return (int)I_AIA.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAI(MemorySegment object, int slot, MemorySegment a, int b) {
        try {
            return (int)I_AAI.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAAA(MemorySegment object, int slot,
            MemorySegment a, MemorySegment b, MemorySegment c) {
        try {
            return (int)I_AAAA.invokeExact(function(object, slot), object, a, b, c);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAAAAAA(MemorySegment object, int slot, MemorySegment a, MemorySegment b,
            MemorySegment c, MemorySegment d, MemorySegment e, MemorySegment f) {
        try {
            return (int)I_AAAAAAA.invokeExact(function(object, slot), object, a, b, c, d, e, f);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAII(MemorySegment object, int slot, int a, int b) {
        try {
            return (int)I_AII.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAIAA(MemorySegment object, int slot, int a, MemorySegment b, MemorySegment c) {
        try {
            return (int)I_AIAA.invokeExact(function(object, slot), object, a, b, c);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAIIIII(MemorySegment object, int slot, int a, int b, int c, int d, int e) {
        try {
            return (int)I_AIIIII.invokeExact(function(object, slot), object, a, b, c, d, e);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAIIAAAA(MemorySegment object, int slot, int a, int b,
            MemorySegment c, MemorySegment d, MemorySegment e, MemorySegment f) {
        try {
            return (int)I_AIIAAAA.invokeExact(function(object, slot), object, a, b, c, d, e, f);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAI(MemorySegment object, int slot, int a) {
        try {
            return (int)I_AI.invokeExact(function(object, slot), object, a);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAIALAA(MemorySegment object, int slot, int a, MemorySegment b, long c,
            MemorySegment d, MemorySegment e) {
        try {
            return (int)I_AIALAA.invokeExact(function(object, slot), object, a, b, c, d, e);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAIAIAAA(MemorySegment object, int slot, MemorySegment a, int b,
            MemorySegment c, int d, MemorySegment e, MemorySegment f, MemorySegment g) {
        try {
            return (int)I_AAIAIAAA.invokeExact(function(object, slot), object, a, b, c, d, e, f, g);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntALIAA(MemorySegment object, int slot, long a, int b,
            MemorySegment c, MemorySegment d) {
        try {
            return (int)I_ALIAA.invokeExact(function(object, slot), object, a, b, c, d);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntAAL(MemorySegment object, int slot, MemorySegment a, long b) {
        try {
            return (int)I_AAL.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAAAL(MemorySegment object, int slot,
            MemorySegment a, MemorySegment b, long c) {
        try {
            V_AAAL.invokeExact(function(object, slot), object, a, b, c);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAAL(MemorySegment object, int slot, MemorySegment a, long b) {
        try {
            V_AAL.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAILLI(MemorySegment object, int slot, int a, long b, long c, int d) {
        try {
            V_AILLI.invokeExact(function(object, slot), object, a, b, c, d);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAAIILAAAA(MemorySegment object, int slot, MemorySegment a,
            int b, int c, long d, MemorySegment e, MemorySegment f, MemorySegment g, MemorySegment h) {
        try {
            V_AAIILAAAA.invokeExact(function(object, slot), object, a, b, c, d, e, f, g, h);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIIII(MemorySegment object, int slot, int a, int b, int c, int d) {
        try {
            V_AIIII.invokeExact(function(object, slot), object, a, b, c, d);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIIIII(MemorySegment object, int slot, int a, int b, int c, int d, int e) {
        try {
            V_AIIIII.invokeExact(function(object, slot), object, a, b, c, d, e);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAAIIIAA(MemorySegment object, int slot, MemorySegment a,
            int b, int c, int d, MemorySegment e, MemorySegment f) {
        try {
            V_AAIIIAA.invokeExact(function(object, slot), object, a, b, c, d, e, f);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAI(MemorySegment object, int slot, int a) {
        try {
            V_AI.invokeExact(function(object, slot), object, a);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIA(MemorySegment object, int slot, int a, MemorySegment b) {
        try {
            V_AIA.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAA(MemorySegment object, int slot, MemorySegment a) {
        try {
            V_AA.invokeExact(function(object, slot), object, a);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIL(MemorySegment object, int slot, int a, long b) {
        try {
            V_AIL.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIIA(MemorySegment object, int slot, int a, int b, MemorySegment c) {
        try {
            V_AIIA.invokeExact(function(object, slot), object, a, b, c);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidAIAIA(MemorySegment object, int slot,
            int a, MemorySegment b, int c, MemorySegment d) {
        try {
            V_AIAIA.invokeExact(function(object, slot), object, a, b, c, d);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidALIFBIA(MemorySegment object, int slot, long a, int b,
            float c, byte d, int e, MemorySegment f) {
        try {
            V_ALIFBIA.invokeExact(function(object, slot), object, a, b, c, d, e, f);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comVoidALAIA(MemorySegment object, int slot,
            long a, MemorySegment b, int c, MemorySegment d) {
        try {
            V_ALAIA.invokeExact(function(object, slot), object, a, b, c, d);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static long comLongA(MemorySegment object, int slot) {
        try {
            return (long)L_A.invokeExact(function(object, slot), object);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comIntALA(MemorySegment object, int slot, long a, MemorySegment b) {
        try {
            return (int)I_ALA.invokeExact(function(object, slot), object, a, b);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static int comMap(MemorySegment object, int subresource, MemorySegment range, MemorySegment output) {
        try {
            return (int)I_AIAA_MAP.invokeExact(function(object, SLOT_RESOURCE_MAP),
                    object, subresource, range, output);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void comUnmap(MemorySegment object, int subresource, MemorySegment range) {
        try {
            V_AIA_UNMAP.invokeExact(function(object, SLOT_RESOURCE_UNMAP), object, subresource, range);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static MemorySegment comAddressAA(MemorySegment object, int slot, MemorySegment output) {
        try {
            return (MemorySegment)A_AA.invokeExact(function(object, slot), object, output);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static MemorySegment comAddressA(MemorySegment object, int slot) {
        try {
            return (MemorySegment)A_A.invokeExact(function(object, slot), object);
        } catch (Throwable error) {
            throw unchecked(error);
        }
    }

    static void release(MemorySegment object) {
        if (!isNull(object)) {
            comIntA(object, SLOT_RELEASE);
        }
    }

    static MemorySegment pointer(MemorySegment output) {
        return output.get(ADDRESS, 0);
    }

    static boolean failed(int result) {
        return result < 0;
    }

    static boolean succeeded(int result) {
        return result >= 0;
    }

    static boolean isNull(MemorySegment value) {
        return value == null || value.address() == 0L;
    }

    static void check(int result, String operation) {
        if (failed(result)) {
            throw new FdxException(operation + ": HRESULT 0x" + String.format("%08X", result));
        }
    }

    private static MethodHandle down(FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(descriptor);
    }

    private static MethodHandle bound(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = lookup.find(name)
                .orElseThrow(() -> new FdxException("Could not find Windows symbol " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MemorySegment function(MemorySegment object, int slot) {
        if (isNull(object)) {
            throw new FdxException("Direct3D 12 COM object is null");
        }
        MemorySegment vtable = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        if (isNull(vtable)) {
            throw new FdxException("Direct3D 12 COM vtable is null");
        }
        return vtable.reinterpret((long)(slot + 1) * ADDRESS.byteSize())
                .get(ADDRESS, (long)slot * ADDRESS.byteSize());
    }

    private static MemorySegment guid(int data1, int data2, int data3, int... data4) {
        if (data4.length != 8) {
            throw new IllegalArgumentException("A GUID requires eight Data4 bytes");
        }
        MemorySegment value = GLOBAL.allocate(SIZE_GUID, 4);
        value.set(INT, 0, data1);
        value.set(SHORT, 4, (short)data2);
        value.set(SHORT, 6, (short)data3);
        for (int index = 0; index < data4.length; index++) {
            value.set(BYTE, 8L + index, (byte)data4[index]);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException unchecked(Throwable error) throws T {
        throw (T)error;
    }
}
