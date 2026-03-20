package com.winlator.cmod.core;

public abstract class DefaultVersion {
    public static final String BOX64 = "0.4.1";
    public static final String WOWBOX64 = "0.4.1";
    public static final String FEXCORE = "2603";
    public static final String WRAPPER = "System";
    public static final String WRAPPER_ADRENO = "turnip_Mar-12-2026_b97a1e6_patched";
    public static final String DXVK = GPUInformation.getRenderer(null, null).contains("Mali") ? "1.10.3" : "2.4.1-gplasync-arm64ec";
    public static final String VKD3D = "proton-3.0b";
}