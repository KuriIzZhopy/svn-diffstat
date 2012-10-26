package com.github.marschal.svndiffstat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

class ResetOutStream extends OutputStream {
	
	private static final String INDEX_PREFIX = "Index: ";
	private static final String MARKER = "===================================================================";
	private static final String OLD_FILE = "--- ";
	private static final String NEW_FILE = "+++ ";
	private static final String ADDED = "+";
	private static final String REMOVED = "-";
	
	private byte[] data;
	private int writePosition;
	private int readPosition;
	
	private int added;
	private int removed;
	
	private byte[] eol;
	private byte[] indexMarker;
	private byte[] marker;
	private byte[] oldFileMarker;
	private byte[] newFileMarker;
	private byte[] addedMarker;
	private byte[] removedMarker;
	private boolean headerParsed;
	
	ResetOutStream() {
		this(0x1FFF);
	}
	
	ResetOutStream(int capacity) {
		this.data = new byte[capacity];
		this.writePosition = 0;
		this.readPosition = 0;
		this.added = 0;
		this.removed = 0;
		this.headerParsed = false;
		// FIXME
		this.eol = System.getProperty("line.separator").getBytes();
		this.setEncoding(System.getProperty("file.encoding"));
	}
	
	
	void initialize() {
		this.added = 0;
		this.removed = 0;
		this.writePosition = 0;
		this.readPosition = 0;
		this.headerParsed = false;
	}
	
	void setEncoding(String encoding) {
		try {
			this.indexMarker = INDEX_PREFIX.getBytes(encoding);
			this.marker = MARKER.getBytes(encoding);
			this.oldFileMarker = OLD_FILE.getBytes(encoding);
			this.newFileMarker = NEW_FILE.getBytes(encoding);
			this.addedMarker = ADDED.getBytes(encoding);
			this.removedMarker = REMOVED.getBytes(encoding);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("encoding " + encoding + " not supported", e);
		}
	}
	
	private boolean startsWith(byte[] b) {
		for (int i = 0; i < b.length; i++) {
			if (this.data[this.readPosition + i] != b[i]) {
				return false;
			}
		}
		return true;
	}
	
	private int indexOf(byte[] b) {
		int dataLength = this.data.length;
		int argumentLength = b.length;
		outerloop : for (int i = this.readPosition; i < dataLength; i++) {
			for (int j = 0; j < argumentLength; j++) {
				if (this.data[i + j] != b[j]) {
					continue outerloop;
				}
			}
			return i;
		}
		return -1;
	}


	@Override
	public void write(int b) throws IOException {
		this.ensureCapacity(1);
		this.data[this.writePosition++] = (byte) b;
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		int len = b.length;
		this.ensureCapacity(len);
		System.arraycopy(b, 0, data, writePosition, len);
		this.writePosition += len;
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		this.ensureCapacity(len);
		System.arraycopy(b, off, data, writePosition, len);
		this.writePosition += len;
	}
	
	private void ensureCapacity(int capacity) {
		if (this.writePosition + capacity > this.data.length) {
			this.parse();
		}
		int length = this.writePosition - this.readPosition;
		System.arraycopy(this.data, this.readPosition, this.data, 0, length);
	}
	
	private void parse() {
		if (!this.headerParsed) {
			// Index: path/to/file.extension
			this.expectMarker(this.indexMarker);
			// EOL
			this.consumeEol();
			// ===================================================================
			this.expectMarker(this.marker);
			// EOL
			this.consumeEol();
			// --- path/to/file.extension (revision x -1)
			this.expectMarker(this.oldFileMarker);
			// EOL
			this.consumeEol();
			// +++ path/to/file.extension (revision x)
			this.expectMarker(this.newFileMarker);
			// EOL
			this.consumeEol();
			this.headerParsed = true;
		}
		int eolIndex = this.indexOf(this.eol);
		while (eolIndex != 1) {
			if (this.startsWith(this.addedMarker)) {
				this.added += 1;
			} else if (this.startsWith(this.removedMarker)) {
				this.removed += 1;
			}
			eolIndex = this.indexOf(this.eol);
		}
	}

	private void expectMarker(byte[] marker) throws IllegalAccessError {
		boolean startsWithHeader = startsWith(marker);
		if (!startsWithHeader) {
			// TODO encoding
			throw new IllegalAccessError(new String(marker) + "expected");
		}
		this.readPosition += marker.length;
	}

	private void consumeEol() {
		int eolIndex = this.indexOf(this.eol);
		if (eolIndex == -1) {
			throw new IllegalArgumentException("EOL expected");
		}
		this.readPosition += eolIndex + this.eol.length;
	}
	
	DiffStat finish() {
		this.parse();
		return new DiffStat(added, removed);
	}


}
