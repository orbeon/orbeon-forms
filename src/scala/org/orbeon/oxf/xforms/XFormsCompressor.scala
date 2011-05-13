/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.apache.commons.pool.PoolableObjectFactory
import org.orbeon.oxf.util._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object XFormsCompressor {

    // Use a Deflater pool as creating deflaters is expensive
    private val deflaterPool = new SoftReferenceObjectPool(new DeflaterPoolableObjectFactory)

    private val BUFFER_SIZE = 1024 * 8
    private val TRAILER_SIZE = 8

    def compressBytes(bytesToEncode: Array[Byte], level: Int) = {
        val deflater = deflaterPool.borrowObject.asInstanceOf[Deflater]
        deflater.setLevel(level)
        try {
            val os = new ByteArrayOutputStream
            val gzipOS = new DeflaterGZIPOutputStream(deflater, os, BUFFER_SIZE)
            gzipOS.write(bytesToEncode)
            gzipOS.close()

            os.toByteArray
        } finally {
            deflaterPool.returnObject(deflater)
        }
    }

    // Compress using BEST_SPEED as serializing state quickly has been determined to be more important than saving extra
    // memory. Even this way compression typically is more than 10X.
    def compressBytes(bytesToEncode: Array[Byte]): Array[Byte] = compressBytes(bytesToEncode, Deflater.BEST_SPEED)

    // Example of effective compression ratios and speeds for XML inputs:
    //
    // Sizes in bytes:
    //
    // Input     | SPEED   | DEFAULT | COMPRESSION
    // ----------+---------+---------+------------
    // 1,485,020 | 130,366 |  90,323 |      85,118
    //   955,373 | 117,509 |  78,538 |      76,461
    //   511,776 |  49,751 |  35,309 |      33,858
    //   178,796 |  17,321 |  12,234 |      11,973
    //
    // Times in ms per compression:
    //
    // Input     | SPEED   | DEFAULT | COMPRESSION
    // ----------+---------+---------+------------
    // 1,485,020 |      15 |      37 |         122
    //   955,373 |      12 |      31 |         108
    //   511,776 |       6 |      13 |          42
    //   178,796 |       2 |       5 |          12

    def compressBytesMeasurePerformance(bytesToEncode: Array[Byte]): Array[Byte] = {

        val settings = Map(
            (Deflater.BEST_SPEED -> "BEST_SPEED"),
            (Deflater.DEFAULT_COMPRESSION -> "DEFAULT_COMPRESSION"),
            (Deflater.BEST_COMPRESSION -> "BEST_COMPRESSION")
        )

        for ((level, description) <- settings) {
            XFormsUtils.indentedLogger.startHandleOperation("compressor", description)
            for (v <- (1 to 100))
                compressBytes(bytesToEncode, level)
            XFormsUtils.indentedLogger.endHandleOperation()
        }

        compressBytes(bytesToEncode, Deflater.BEST_SPEED)
    }

    def uncompressBytes(bytesToDecode: Array[Byte]) = {
        val is = new GZIPInputStream(new ByteArrayInputStream(bytesToDecode))
        val os = new ByteArrayOutputStream(BUFFER_SIZE)
        NetUtils.copyStream(is, os)
        os.toByteArray
    }

    private class DeflaterPoolableObjectFactory extends PoolableObjectFactory {

        def makeObject = {
            XFormsUtils.indentedLogger.logDebug("compressor", "creating new Deflater")
            // Use BEST_SPEED as profiler shows that DEFAULT_COMPRESSION is slower
            new Deflater(Deflater.BEST_SPEED, true)
        }

        def destroyObject(o: AnyRef) = ()
        def validateObject(o: AnyRef) = true
        def activateObject(o: AnyRef) = ()
        def passivateObject(o: AnyRef) {
            try {
                o.asInstanceOf[Deflater].reset()
            } catch {
                case e => XFormsUtils.indentedLogger.logError("compressor", "exception while passivating Deflater", e)
            }
        }
    }

    // GZIPOutputStream which uses a custom Deflater
    private class DeflaterGZIPOutputStream(deflater: Deflater, out: OutputStream, size: Int) extends GZIPOutputStream(out, size) {

        // Super creates deflater, but doesn't yet do anything with it so we override it here
        `def` = deflater

        private var closed = false

        // Override because default implementation calls def.close()
        override def close() =
            if (!closed) {
                finish()
                out.close()
                closed = true
            }
			
		// Override because IBM implementation calls def.end()
		override def finish() {

            def writeTrailer(buf: Array[Byte], offset: Int) {

                def writeInt(i: Int, offset: Int) {

                    def writeShort(s: Int, offset: Int) {
                        buf(offset) =  (s & 0xff).asInstanceOf[Byte]
                        buf(offset + 1) =  ((s >> 8) & 0xff).asInstanceOf[Byte]
                    }

                    writeShort(i & 0xffff, offset)
                    writeShort((i >> 16) & 0xffff, offset + 2)
                }

                writeInt(crc.getValue.toInt, offset) // CRC-32 of uncompr. data
                writeInt(deflater.getTotalIn, offset + 4) // Number of uncompr. bytes
            }

			if (!deflater.finished) {
				deflater.finish()
				while (!deflater.finished) {
					var len = deflater.deflate(buf, 0, buf.length)
					if (deflater.finished && len <= buf.length - TRAILER_SIZE) {
						// last deflater buffer. Fit trailer at the end 
						writeTrailer(buf, len)
						len = len + TRAILER_SIZE
						out.write(buf, 0, len)
						return
                    }
					if (len > 0)
					out.write(buf, 0, len)
				}
				// if we can't fit the trailer at the end of the last
				// deflater buffer, we write it separately
				val trailer = new Array[Byte](TRAILER_SIZE)
				writeTrailer(trailer, 0)
				out.write(trailer)
			}
        }
    }
}
