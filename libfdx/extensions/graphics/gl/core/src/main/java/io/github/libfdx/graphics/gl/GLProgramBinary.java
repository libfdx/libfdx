package io.github.libfdx.graphics.gl;

/** Native program bytes and their implementation-defined format. The producer transfers the
 * byte array to the caller; it must not be changed while a GL call or cache write borrows it. */
public record GLProgramBinary(int format, byte[] bytes) { }
