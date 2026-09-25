# Animated LOD sample

Original libFDX sample geometry: a 1,536-triangle tube, with a blended two-joint
bend and two morph targets (Swell and Stretch). The three glTF variants share
one geometry buffer: skin only, morph only, and morph before skinning. The
combined clip uses cubic weight interpolation; the morph variant uses linear
interpolation. Node weights override the mesh defaults. Position, normal and
tangent deltas are authored together.

Open **ModelLodOptimizerTest**, choose **Bend**, and generate LODs. The two views
play the same animation while detail changes. `libfdx.test.optimizerAnimated=true`
selects the existing preview validator's animated workflows when running a finite
frame count. It exercises all three variants, intermediate and extreme poses,
level switching, automatic distance selection and rebuilding a live chain.
