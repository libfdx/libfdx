# jNative performance comparisons

Latest rendering runs (S8–S13): **2026-10-09**; CPU/kernel rows: **2026-10-08**. Windows x64, Core Ultra 9 275HX. jNative worktree builds, TeaVM C 0.16.0, GraalVM 25.0.0.
**Rightmost = latest measured result; arrows retain earlier measurements. Bold = best latest median.** Lower is better except FPS. All three rendering executables include the same GL capability fix; the compiler/runtime and CPU workloads are unchanged.
T1/T2/T4/T6 share one 500k-pass workload; T3/T5/T7 share its 1M-pass version. Each adjacent C pair also shares a workload. Historical IDs remain for comparison and reuse the same current measurement; they are not separate unresolved issues.
CPU and trig: 20 samples/runtime/case across two rounds; other kernels and staging: 10. These use CPU 0. Rendering: normal scheduling, 4 runs/runtime, 5 s warmup + 10 s measured. Cross-session arrows alone do not establish an optimization's effect; the gains below compare retained old/new jNative executables within the same run sets.

| ID | Measurement | jNative | TeaVM C | GraalVM |
| --- | --- | ---: | ---: | ---: |
| T1 | Trig/table loop, 500k passes (ns/op) ↻ | 3.597 → 1.360 → 1.339 → 1.364 → 1.309 → 1.499 | 4.033 → 4.003 → 3.823 → 4.162 → 3.714 → 4.080 | 1.336 → 1.366 → 1.321 → 1.353 → 1.311 → **1.431** |
| T2 | Cached-table loop, 500k passes — covered by T1 (ns/op) ↻ | 3.342 → 2.919 → 1.405 → 1.339 → 1.364 → 1.309 → 1.499 | 3.710 → 4.361 → 3.823 → 4.162 → 3.714 → 4.080 | 1.306 → 1.401 → 1.321 → 1.353 → 1.311 → **1.431** |
| T3 | Trig/table loop, 1M passes (ns/op) ↻ | 3.227 → 3.023 → 1.278 → 1.343 → 1.302 → 1.433 | 4.093 → 3.790 → 3.950 → 3.913 → 4.135 | 1.328 → 1.262 → 1.321 → 1.281 → **1.379** |
| T4 | Optimized loop, 500k passes (ns/op) ↻ | 3.012 → 1.639 → 1.712 → 1.800 → 1.339 → 1.364 → 1.309 → 1.499 | 3.769 → 4.078 → 4.206 → 3.823 → 4.162 → 3.714 → 4.080 | 1.316 → 1.381 → 1.415 → 1.321 → 1.353 → 1.311 → **1.431** |
| T5 | Optimized loop, 1M passes (ns/op) ↻ | 2.909 → 1.631 → 1.669 → 1.701 → 1.278 → 1.343 → 1.302 → 1.433 | 3.731 → 3.934 → 3.883 → 3.790 → 3.950 → 3.913 → 4.135 | 1.312 → 1.339 → 1.361 → 1.262 → 1.321 → 1.281 → **1.379** |
| T6 | Single-allocation float arrays, 500k passes (prototype → production, ns/op) ↻ | 1.417 → 1.339 → 1.364 → 1.309 → 1.499 | 4.206 → 3.823 → 4.162 → 3.714 → 4.080 | 1.415 → 1.321 → 1.353 → 1.311 → **1.431** |
| T7 | Single-allocation float arrays, 1M passes (prototype → production, ns/op) ↻ | 1.407 → 1.278 → 1.343 → 1.302 → 1.433 | 3.883 → 3.790 → 3.950 → 3.913 → 4.135 | 1.361 → 1.262 → 1.321 → 1.281 → **1.379** |
| C1 | Cold-root kernel (ns/op) ↻ | 6.873 → 5.892 → 5.492 → 3.803 → 4.201 | 7.286 → 8.004 → 7.758 → 7.481 → 9.715 | 2.608 → 2.756 → 2.787 → 2.756 → **3.262** |
| C2 | Cold-root after loop optimization (ns/op) ↻ | 7.096 → 5.984 → 5.971 → 5.892 → 5.492 → 3.803 → 4.201 | 8.010 → 8.090 → 8.004 → 7.758 → 7.481 → 9.715 | 2.681 → 2.658 → 2.756 → 2.787 → 2.756 → **3.262** |
| C3 | Plain-root control (ns/op) ↻ | 5.410 → 3.815 → 3.432 → 3.321 → 3.975 | 6.663 → 7.052 → 7.054 → 7.402 → 8.337 | 2.534 → 2.663 → 2.731 → 2.659 → **3.098** |
| C4 | Plain-root after loop optimization (ns/op) ↻ | 5.572 → 4.350 → 4.377 → 3.815 → 3.432 → 3.321 → 3.975 | 6.876 → 6.916 → 7.052 → 7.054 → 7.402 → 8.337 | 2.658 → 2.594 → 2.663 → 2.731 → 2.659 → **3.098** |
| C5 | Array-copy kernel (ns/op) ↻ | 7.649 → 6.262 → 5.808 → 5.295 → 6.492 | 10.253 → 10.445 → 10.428 → 10.275 → 11.342 | 4.068 → 4.203 → 4.242 → 4.200 → **5.269** |
| C6 | Array-copy after loop optimization (ns/op) ↻ | 8.214 → 6.682 → 6.439 → 6.262 → 5.808 → 5.295 → 6.492 | 9.810 → 10.552 → 10.445 → 10.428 → 10.275 → 11.342 | 4.126 → 4.171 → 4.203 → 4.242 → 4.200 → **5.269** |
| C7 | Scalar-copy control (ns/op) ↻ | 19.788 → 12.210 → 12.459 → 12.206 → 13.967 | 16.789 → 17.943 → 18.900 → 22.061 → 20.089 | 10.998 → 11.577 → 11.933 → 11.570 → **12.554** |
| C8 | Scalar-copy after loop optimization (ns/op) ↻ | 19.606 → 12.173 → 12.289 → 12.210 → 12.459 → 12.206 → 13.967 | 19.906 → 17.260 → 17.943 → 18.900 → 22.061 → 20.089 | 11.305 → 11.349 → 11.577 → 11.933 → 11.570 → **12.554** |
| C9 | Scale-chain kernel (ns/op) ↻ | 7.403 → 4.700 → 4.890 → 4.762 → **4.843** | 8.619 → 8.604 → 8.981 → 8.772 → 9.551 | 4.328 → 4.284 → 4.518 → 4.396 → 4.865 |
| C10 | Scale-chain after loop optimization (ns/op) ↻ | 7.341 → 4.929 → 5.247 → 4.700 → 4.890 → 4.762 → **4.843** | 8.509 → 8.742 → 8.604 → 8.981 → 8.772 → 9.551 | 4.277 → 4.320 → 4.284 → 4.518 → 4.396 → 4.865 |
| C11 | Fused-scale control (ns/op) ↻ | 5.953 → 4.609 → 4.424 → 3.748 → 3.899 | 8.170 → 8.094 → 8.460 → 8.282 → 9.040 | 3.600 → 3.614 → 3.502 → 3.518 → **3.720** |
| C12 | Fused-scale after loop optimization (ns/op) ↻ | 5.685 → 4.819 → 5.131 → 4.609 → 4.424 → 3.748 → 3.899 | 8.598 → 8.108 → 8.094 → 8.460 → 8.282 → 9.040 | 3.488 → 3.466 → 3.614 → 3.502 → 3.518 → **3.720** |
| S1 | Full sprite CPU work (ns/sprite) ↻ | 24.463 → 27.511 → 25.656 → 24.689 | 21.050 → 23.188 → 21.467 → 26.361 | 8.957 → 9.831 → 9.078 → **11.727** |
| S2 | Transform only (ns/sprite) ↻ | 10.390 → 10.705 → 9.719 → 12.026 | 11.375 → 11.951 → 11.318 → 13.304 | 3.692 → 4.064 → 3.825 → **4.712** |
| S3 | Cached CpuSprite.draw (ns/sprite) ↻ | 21.139 → 22.452 → 21.199 → 16.538 | 13.587 → 14.424 → 13.848 → 16.087 | 4.169 → 4.681 → 4.288 → **5.362** |
| S4 | CpuSpriteBatch.draw (ns/sprite) ↻ | 14.906 → 15.626 → 13.388 → 14.022 | 10.802 → 11.689 → 10.978 → 13.117 | 3.876 → 4.322 → 4.063 → **5.044** |
| S5 | Append vertices (ns/sprite) ↻ | 10.145 → 9.507 → 8.246 → 7.586 | 7.397 → 8.132 → 7.258 → 8.926 | 4.329 → 4.595 → 4.414 → **5.335** |
| S6 | Direct vertex copy (ns/sprite) ↻ | 9.294 → 9.293 → 8.027 → 7.333 | 6.939 → 7.489 → 7.060 → 8.677 | 3.217 → 3.446 → 3.306 → **4.913** |
| S7 | Buffer staging (µs/transfer) ↻ | 24.773 → 22.182 → 23.403 → 25.665 | 8.737 → 9.562 → 9.067 → **12.156** | 12.242 → 12.803 → 12.453 → 16.538 |
| S8 | Full rendering (FPS, higher is better) ↻ | 2788.480 → 3062.920 → 2904.880 → 3028.045 → 2954.000 | 3345.970 → 3680.295 → 3372.630 → 2868.230 → 2994.690 | 4295.140 → 4701.685 → 4193.315 → 3695.330 → **3350.445** |
| S9 | Profiled sprite fill (µs/frame) ↻ | 245.473 → 246.588 → 257.784 → 232.329 → 233.843 | 201.424 → 192.151 → 217.455 → 217.163 → 222.261 | 83.174 → 83.799 → 106.350 → 89.112 → **92.942** |
| S10 | Profiled buffer staging (µs/frame) ↻ | 26.360 → 26.885 → 26.220 → 30.960 → 32.146 | 14.180 → 11.756 → 13.440 → 14.568 → **14.540** | 19.575 → 18.047 → 23.632 → 21.224 → 22.382 |
| S11 | Profiled graphics upload (µs/frame) ↻ | 11.546 → 17.858 → 27.671 → 13.299 → 25.400 | 27.554 → 28.900 → 10.725 → 45.996 → 16.764 | 14.509 → 14.253 → 14.339 → 15.483 → **16.677** |
| S12 | Profiled draw submission (µs/frame) ↻ | 23.544 → 20.629 → 19.452 → 32.175 → 4.449 | 21.173 → 17.482 → 18.913 → 24.813 → **2.420** | 10.254 → 8.186 → 10.255 → 10.865 → 11.308 |
| S13 | Profiled outside-render time (µs/frame) ↻ | 24.762 → 24.951 → 24.339 → 29.319 → **40.737** | 23.478 → 23.081 → 23.867 → 28.238 → 41.718 | 95.991 → 79.554 → 78.289 → 103.163 → 119.919 |

**S12 fix:** reuse GL capabilities already captured for each attachment's context, instead of querying the driver on every pipeline bind. Profiling isolated variable native capability-query cost; managed validation and uniform copying were small. This shared provider improvement preserves render-state commands and applies to all three runtimes. No benchmark or jNative compiler/runtime change.

**Matched results:** jNative submission **26.289 → 4.449 µs (83.1% less time)**; all four candidate runs were 4.05–4.75 µs. Rendering **2,712.520 → 2,954.000 FPS (+8.9%)**; four nearby old/new pairs improved 0.7–29.2%. Other phases still vary: profiled upload was 25.3% slower in this set. Phase changes should not be treated as independent speedups.

**Next S12 target:** prepare immutable pipeline/vertex state once and submit it through fewer native calls. Driver-free controls identify about **0.90 µs of wrapper overhead** plus **0.89 µs of additional managed work** in jNative. These are diagnostic costs, not new rendering results; GC and error handling must remain intact. [Measurements and method](../build/s12-bindings-20261009/commands.txt).

**Earlier CPU gains retained:** full sprite CPU time 16.8% lower, cached draws 32.4%, batch draws 9.3%; transform/staging remain gaps. Previous compiler changes moved root registration into collecting paths and reduced primitive-array headers from 72 to 40 bytes. GraalVM still leads most CPU workloads.

**Validation:** 78 GL tests passed, including capability reuse, shared contexts, state-call order and loss/disposal. All three Windows x64 native builds passed; 32 final profile/render processes passed frame accounting and identity checks, and an actual rendered frame was inspected. Other platforms were not run. Earlier compiler/GC validation is linked below; TeaVM's earlier full kernel-verification failure remains unresolved.

[Current S12 investigation, samples and commands](../build/s12-performance-20261009/commands.txt) · [Previous S measurements](../build/s-performance-20261008/commands.txt) · [Previous C/T results](../build/root-handoff-20261008/commands.txt) · [Earlier C/T results](../build/c-kernels-20261008/commands.txt) · [Previous kernel/application results](../build/performance-broader-20261007/commands.txt) · [T1 runtime measurements](../build/t1-production-20261007/commands.txt) · [T2 revalidation](../build/t2-validation-20261007/commands.txt) · [Previous rerun](../build/performance-rerun-20261007/commands.txt) · [Earlier compiler results](../build/t1-full-20261007/commands.txt) · [Original comparison](../build/performance-refresh-20261007/commands.txt).
