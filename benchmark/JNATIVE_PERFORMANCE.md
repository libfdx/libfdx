# jNative performance comparisons

Runs: **2026-10-07**, Windows x64, Core Ultra 9 275HX. jNative worktree builds, TeaVM C 0.16.0, GraalVM 25.0.0.
Medians; **bold = best measured median**. Lower is better except FPS. Each row has all three runtimes from the same round; arrows show matched jNative before → after.
Kernels and CPU/staging: CPU 0, 10 samples/runtime/case. Rendering: normal scheduling, 4 runs/runtime, 5 s warmup + 10 s measured.

| ID | Measurement | jNative | TeaVM C | GraalVM |
| --- | --- | ---: | ---: | ---: |
| T1 | Trig/table baseline (ns/op) | 3.597 | 4.033 | **1.336** |
| T1 | Cached table, 500k passes (ns/op) | 3.342 → 2.919 | 3.710 | **1.306** |
| T1 | Cached table, 1M passes (ns/op) | 3.227 → 3.023 | 4.093 | **1.328** |
| T1 | Optimized loop, 500k passes (ns/op) | 3.012 → 1.639 | 3.769 | **1.316** |
| T1 | Optimized loop, 1M passes (ns/op) | 2.909 → 1.631 | 3.731 | **1.312** |
| C1 | Cold-root kernel (ns/op) | 6.873 | 7.286 | **2.608** |
| C1 | Cold-root after T1 (ns/op) | 7.096 → 5.984 | 8.010 | **2.681** |
| C1 | Plain-root control (ns/op) | 5.410 | 6.663 | **2.534** |
| C1 | Plain-root after T1 (ns/op) | 5.572 → 4.350 | 6.876 | **2.658** |
| C2 | Array-copy kernel (ns/op) | 7.649 | 10.253 | **4.068** |
| C2 | Array-copy after T1 (ns/op) | 8.214 → 6.682 | 9.810 | **4.126** |
| C2 | Scalar-copy control (ns/op) | 19.788 | 16.789 | **10.998** |
| C2 | Scalar-copy after T1 (ns/op) | 19.606 → 12.173 | 19.906 | **11.305** |
| C3 | Scale-chain kernel (ns/op) | 7.403 | 8.619 | **4.328** |
| C3 | Scale-chain after T1 (ns/op) | 7.341 → 4.929 | 8.509 | **4.277** |
| C3 | Fused-scale control (ns/op) | 5.953 | 8.170 | **3.600** |
| C3 | Fused-scale after T1 (ns/op) | 5.685 → 4.819 | 8.598 | **3.488** |
| S1 | Full sprite CPU work (ns/sprite) | 24.463 | 21.050 | **8.957** |
| S1 | Transform only (ns/sprite) | 10.390 | 11.375 | **3.692** |
| S2 | Cached Sprite.draw (ns/sprite) | 21.139 | 13.587 | **4.169** |
| S2 | Batch.draw (ns/sprite) | 14.906 | 10.802 | **3.876** |
| S3 | Append vertices (ns/sprite) | 10.145 | 7.397 | **4.329** |
| S3 | Direct vertex copy (ns/sprite) | 9.294 | 6.939 | **3.217** |
| S4 | Buffer staging (µs/transfer) | 24.773 | **8.737** | 12.242 |
| S6 | Full rendering (FPS, higher is better) | 2788.480 | 3345.970 | **4295.140** |
| S1 | Profiled sprite fill (µs/frame) | 245.473 | 201.424 | **83.174** |
| S4 | Profiled buffer staging (µs/frame) | 26.360 | **14.180** | 19.575 |
| S6 | Profiled graphics upload (µs/frame) | **11.546** | 27.554 | 14.509 |
| S6 | Profiled draw submission (µs/frame) | 23.544 | 21.173 | **10.254** |
| S7 | Profiled outside-render time (µs/frame) | 24.762 | **23.478** | 95.991 |

Baseline validation: all 336 kernel checksums and 252 application states matched; all 24 rendering/profile runs passed workload/accounting checks.
TeaVM passed the finite timed workloads but failed full kernel verification before the first conversion result; its timings do not establish full Java conversion correctness.
Profile rows are separate instrumented runs; their phase times are not CPU-isolate costs. Results describe these builds and this machine.
Recorded rendering/profile results predate the texture replacement with `benchmark/assets/fdx.png`.

**T1: 43.9–45.6% less time than the cached-table build above.** Hoist stable final input/output metadata, guard array ranges and masked indices, poll GC in bounded 64-iteration chunks, and avoid materializing array views on the stack. Atomic accesses, conversion semantics and checked fallbacks are preserved. jNative is still about **25% slower than GraalVM**; parity is not reached.
38 backend tests and 18 Debug/Release native checks passed; 576 kernel checksum records matched within each case and round. Desktop build and 144 application state checks passed. No application FPS gain is claimed. “After T1” kernel controls use 50k passes; other rows retain their complete baseline.
[Current T1 samples, build identities, validation and commands](../build/t1-full-20261007/commands.txt) · [Earlier table-cache results](../build/t1-static-table-20261007/commands.txt) · [Original full comparison](../build/performance-refresh-20261007/commands.txt).



