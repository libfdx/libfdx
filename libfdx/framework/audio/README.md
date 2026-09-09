# Audio

Launchers explicitly attach an `AudioProvider` with their backend configuration's
`audio(provider)` method. Desktop JVM and browser backends expose the resulting
backend-owned service through `fdx.audio()`. With no provider, that root is null;
device setup failure aborts startup. Other backends retain a null audio root.

`PcmData` is immutable CPU data. `audio.createSound(pcm)` copies it into an
application-owned sound in that service's resource domain. A sound supports many
simultaneous voices; each `play` returns a primitive handle. The fixed pool rejects
excess voices with `Audio.NO_VOICE`. It does not steal existing voices. Handles
cannot affect another service or a replacement voice after stop/completion.

```java
Sound effect = audio.createSound(pcm);
long voice = audio.play(effect, 0.5f, 1f, -0.25f, false);
audio.pause(voice);
audio.parameters(voice, 0.4f, 1.25f, 0.25f);
audio.resume(voice);
effect.dispose(); // stops all voices using this sound
```

Call controls, status queries, and disposal on the application thread. The backend
polls completion each loop iteration. Native/browser audio processing continues
independently of rendering. There are no application callbacks from a mixer thread.
Gain is 0–1, pitch 0.25–4, and pan -1 (left) to +1 (right). Mono pan uses equal power;
stereo pan attenuates the opposite channel. Looping repeats the whole sound.
Suspension retains positions and individual pause states. Browser applications
call `audio.resume()` from a pointer, touch, or keyboard gesture and observe its
future and `isSuspended()`; a queued voice is not evidence of audible output.

The optional `audio_loaders` module registers bounded PCM WAV decoding and a
dependent sound loader with `AudioAssetLoaders.register(assets, audio)`. File
acquisition and decoding use the asset manager's async pipeline; device upload
runs as a budgeted application-thread finalizer. A manager borrows one audio
service. Close its scopes/manager before the backend shuts that service down.
The final asset owner stops sound voices before releasing the decoded dependency.

Supported WAV input is RIFF PCM, mono/stereo, unsigned 8-bit or signed 16-bit,
8–192 kHz. Malformed/unsupported files fail explicitly. The default decoded limit
is 1,440,000 frames. It bounds decoded allocation, not encoded file acquisition.
This short-sound loader decodes the full input; use streamed music for long tracks.

`audio.createMusic(pcmStream, buffering)` transfers the owned `PcmStream` on success
only. Creation failure leaves it with the caller. Four live streams are supported
by the OpenAL and Web Audio providers, independently of the sound voice limit;
capacity exhaustion fails explicitly. Each stream defaults to four queued blocks
of 4,096 frames plus one decoded block. Memory depends on this configuration and
channel count, not track length. Initial playback/recovery waits for two blocks,
or the available final block at EOF. Queue starvation freezes the media position
and increments `underruns()`; update refills it without skipping missed samples.

`Music` supports play/pause/stop, gain/pan, absolute frame seeks, and half-open
loop intervals. Seeking/looping require a seekable source. Stop discards queued
audio and rewinds; a consumed sequential source cannot restart. Individual pause
survives service suspension. Read failure sets that music's `FAILED` state and
retains `failure()` without stopping unrelated playback. Dispose closes its PCM
input, cancels pending work, and releases queued native resources. Shutdown closes
remaining music before the provider. Music controls and inspection run on the
application thread; no decoder or application callback runs on the native mixer.

`WavStream.openFile(file, blockFrames, executor)` opens a bounded stream using the
[file source contract](../files/README.md). Schedule opening through
`AssetLoadContext.asyncFuture` or a worker because packaged/native I/O may be
synchronous. WAV headers are bounded to 64 KiB; format precedes the final data
chunk. Unsupported/truncated inputs fail explicitly. Subsequent reads use the
borrowed bounded executor, retrying full queues through update; null uses
cooperative reads. Dispose music before closing that executor. Packaged sequential
input supports ordinary playback; seek/loop require a seekable source. Browser
uncached streams require valid uncompressed HTTP ranges and explicit deferred
asset paths in `WebApplicationConfig`, as described by the file guide.

`AudioAssetLoaders.registerMusic(assets, audio, buffering, executor)` registers
budgeted header preparation and application-thread music creation. The buffer
configuration is fixed for that manager registration. A cached `Music` is one
shared playback instance, including controls and position. For independent voices
of the same track, open separate streams and create separate music instances.

`AudioMixer` borrows the audio domain, sounds, and registered music. Its master and
MUSIC/SFX/UI bus gains multiply each playback's base gain. Bookkeeping is bounded
by the service capacities. Disposing the mixer stops its playback but leaves
assets owned by the application or scopes. Detached music retains its last gain.

```java
AudioMixer mixer = new AudioMixer(audio);
mixer.masterGain(0.8f).busGain(AudioBus.SFX, 0.6f);
long effect = mixer.play(AudioBus.SFX, sound, 0.5f, 1, 0, false);
mixer.music(firstTrack, 0.7f).music(secondTrack, 0.7f);
firstTrack.play();
mixer.crossfade(firstTrack, secondTrack, 2); // linear gain weights; pauses outgoing
mixer.update(deltaSeconds); // each application iteration; no allocation here
```

A fade uses caller-supplied elapsed time and advances at update boundaries. One
fade is active per mixer; replacing it retains current weights, including any
previously involved track outside the new pair. There is no background game-time
timer or sample-accurate gain automation. Buffer reads create small task/future
objects at block boundaries; steady sound controls and mixer updates reuse storage.

The optional `audio_openal` provider uses LWJGL OpenAL Soft on desktop JVM. Each
logical voice reserves two sources; each sound stores two stereo buffers, one
channel active in each, for consistent balance independent of spatialization.
It requires `AL_SOFT_direct_channels` and routes sound and music sources directly
so OpenAL headphone virtualization does not mix a hard-panned signal into both ears.
Native PCM storage is eight bytes per source frame. Music uses the same routing
with two sources and a fixed set of reusable queued buffers. `OpenALAudio.loopback` is an
explicit offline renderer for capture/testing and never a missing-device fallback.
The optional `audio_web` provider uses Web Audio. Browser source nodes allocate on
play/resume; Java voice storage is reused. Output routing, latency, and device
changes remain platform-controlled. Streamed browser blocks allocate AudioBuffers
and scheduled source nodes, with a 5 ms scheduling lead when starting/resuming.
There is no claim of zero browser allocation, automatic output-device recovery,
spatial audio, or compressed decoding in these playback providers.

The shared `audio-playback` scenario loads one WAV through two scopes, saturates
32 voices, changes playback controls, and releases the final owner while rendering.
Click/tap or press a key to activate it in a fresh browser session.
The `music-streaming` scenario loads two deferred WAVs, crossfades, seeks/loops,
suspends/resumes, and releases both tracks while retaining 32 bounded SFX slots.
