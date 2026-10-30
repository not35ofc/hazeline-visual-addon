package com.hazeline.music;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal COM / WinRT plumbing over JNA (JNA 5.17 ships with Minecraft 1.21.11).
 *
 * <p>Interfaces are called through their vtables: slot 0-2 = IUnknown (QueryInterface, AddRef,
 * Release), slot 3-5 = IInspectable (GetIids, GetRuntimeClassName, GetTrustLevel) for WinRT
 * interfaces, then the interface's own methods in declaration order. Slot numbers used by the
 * callers were read from the windows-rs generated bindings (WinRT) and Wine's IDL files (Core Audio).
 *
 * <p>Everything here must only be called from the {@link MediaService} worker thread (it is the
 * thread that called {@code RoInitialize}).
 */
final class Com {
	static final int S_OK = 0;

	/** x64 has a single calling convention (= C); stdcall (JNA's ALT_CONVENTION) only exists on 32-bit Windows. */
	private static final int CONV = Native.POINTER_SIZE == 8 ? Function.C_CONVENTION : Function.ALT_CONVENTION;

	private Com() {
	}

	/** Thrown for a failed HRESULT. */
	static final class ComException extends RuntimeException {
		final int hr;

		ComException(String what, int hr) {
			super(what + " failed: 0x" + Integer.toHexString(hr));
			this.hr = hr;
		}
	}

	static Function fn(String dll, String name) {
		return Function.getFunction(dll, name, CONV);
	}

	/** Calls vtable slot {@code slot} of {@code obj}; returns the HRESULT. */
	static int call(Pointer obj, int slot, Object... args) {
		Pointer vtable = obj.getPointer(0);
		Pointer method = vtable.getPointer((long) slot * Native.POINTER_SIZE);
		Object[] all = new Object[args.length + 1];
		all[0] = obj;
		System.arraycopy(args, 0, all, 1, args.length);
		return Function.getFunction(method, CONV).invokeInt(all);
	}

	static void check(int hr, String what) {
		if (hr < 0) {
			throw new ComException(what, hr);
		}
	}

	/** Calls a getter that returns an interface pointer (slot(this, out)); null if the result is null. */
	static Pointer getPtr(Pointer obj, int slot, String what) {
		PointerByReference out = new PointerByReference();
		check(call(obj, slot, out), what);
		return out.getValue();
	}

	/** Like {@link #getPtr} for methods with arguments before the out pointer (slot(this, args..., out)). */
	static Pointer getPtrWith(Pointer obj, int slot, String what, Object... args) {
		PointerByReference out = new PointerByReference();
		Object[] all = new Object[args.length + 1];
		System.arraycopy(args, 0, all, 0, args.length);
		all[args.length] = out;
		check(call(obj, slot, all), what);
		return out.getValue();
	}

	static void release(Pointer obj) {
		if (obj != null) {
			call(obj, 2);
		}
	}

	/** QueryInterface; null if the object does not implement {@code iid}. */
	static Pointer query(Pointer obj, Memory iid) {
		PointerByReference out = new PointerByReference();
		int hr = call(obj, 0, iid, out);
		return hr < 0 ? null : out.getValue();
	}

	/** A GUID in memory from "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx". */
	static Memory guid(String s) {
		String h = s.replace("-", "");
		if (h.length() != 32) {
			throw new IllegalArgumentException(s);
		}
		Memory m = new Memory(16);
		m.setInt(0, (int) Long.parseLong(h.substring(0, 8), 16));
		m.setShort(4, (short) Integer.parseInt(h.substring(8, 12), 16));
		m.setShort(6, (short) Integer.parseInt(h.substring(12, 16), 16));
		for (int i = 0; i < 8; i++) {
			m.setByte(8 + i, (byte) Integer.parseInt(h.substring(16 + i * 2, 18 + i * 2), 16));
		}
		return m;
	}

	// ------------------------------------------------------------------------ HSTRING

	private static Function createString;
	private static Function deleteString;
	private static Function rawBuffer;

	private static void ensureStrings() {
		if (createString == null) {
			createString = fn("combase", "WindowsCreateString");
			deleteString = fn("combase", "WindowsDeleteString");
			rawBuffer = fn("combase", "WindowsGetStringRawBuffer");
		}
	}

	/** Creates an HSTRING; free it with {@link #deleteHString}. */
	static Pointer hstring(String s) {
		ensureStrings();
		PointerByReference out = new PointerByReference();
		check(createString.invokeInt(new Object[] {new WString(s), s.length(), out}), "WindowsCreateString");
		return out.getValue();
	}

	static void deleteHString(Pointer h) {
		if (h != null) {
			ensureStrings();
			deleteString.invokeInt(new Object[] {h});
		}
	}

	/** Reads and frees an HSTRING (null HSTRING = empty string). */
	static String takeHString(Pointer h) {
		if (h == null) {
			return "";
		}
		ensureStrings();
		try {
			IntByReference len = new IntByReference();
			Pointer buf = (Pointer) rawBuffer.invoke(Pointer.class, new Object[] {h, len});
			int n = len.getValue();
			if (buf == null || n <= 0) {
				return "";
			}
			return utf16(buf, Math.min(n, 4096));
		} finally {
			deleteHString(h);
		}
	}

	/**
	 * Reads {@code chars} UTF-16 code units. (Not Pointer.getCharArray: JNA maps char to wchar_t, which is
	 * 2 bytes on Windows but 4 on Linux - reading the bytes keeps this identical everywhere.)
	 */
	static String utf16(Pointer p, int chars) {
		return new String(p.getByteArray(0, chars * 2), java.nio.charset.StandardCharsets.UTF_16LE);
	}

	/** Getter returning an HSTRING (slot(this, HSTRING*)). */
	static String getString(Pointer obj, int slot, String what) {
		PointerByReference out = new PointerByReference();
		check(call(obj, slot, out), what);
		return takeHString(out.getValue());
	}

	// ------------------------------------------------------------------------ simple getters

	static boolean getBool(Pointer obj, int slot, String what) {
		Memory out = new Memory(4);
		out.setInt(0, 0);
		check(call(obj, slot, out), what);
		return out.getByte(0) != 0;
	}

	static int getInt(Pointer obj, int slot, String what) {
		IntByReference out = new IntByReference();
		check(call(obj, slot, out), what);
		return out.getValue();
	}

	static long getLong(Pointer obj, int slot, String what) {
		Memory out = new Memory(8);
		out.setLong(0, 0L);
		check(call(obj, slot, out), what);
		return out.getLong(0);
	}

	// ------------------------------------------------------------------------ IAsyncOperation

	static final Memory IID_IASYNCINFO = guid("00000036-0000-0000-c000-000000000046");
	private static final int ASYNC_STATUS = 7; // IAsyncInfo: Id 6, Status 7, ErrorCode 8, Cancel 9, Close 10
	private static final int ASYNC_ERROR = 8;
	private static final int ASYNC_CANCEL = 9;
	private static final int ASYNC_CLOSE = 10;
	/** IAsyncOperation<T>: SetCompleted 6, Completed 7, GetResults 8. */
	static final int ASYNC_GET_RESULTS = 8;

	/**
	 * Waits (by polling, no completion delegate needed) until {@code op} finished. Returns true when it
	 * completed successfully; on error / timeout it is cancelled. The operation itself is not released
	 * and NOT closed: IAsyncInfo.Close() makes GetResults fail with E_ILLEGAL_METHOD_CALL, so callers
	 * read the result first and then call {@link #close}.
	 */
	static boolean await(Pointer op, long timeoutMs, String what) {
		Pointer info = query(op, IID_IASYNCINFO);
		if (info == null) {
			throw new IllegalStateException(what + ": no IAsyncInfo");
		}
		try {
			long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
			int sleep = 1;
			while (true) {
				int status = getInt(info, ASYNC_STATUS, what + " status");
				if (status == 1) { // Completed
					return true;
				}
				if (status == 2 || status == 3) { // Canceled / Error
					if (status == 3) {
						IntByReference err = new IntByReference();
						call(info, ASYNC_ERROR, err);
						MediaService.debug(what + " error 0x" + Integer.toHexString(err.getValue()));
					}
					return false;
				}
				if (System.nanoTime() > deadline) {
					call(info, ASYNC_CANCEL);
					MediaService.debug(what + " timed out");
					return false;
				}
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					call(info, ASYNC_CANCEL);
					return false;
				}
				sleep = Math.min(sleep * 2, 16);
			}
		} finally {
			release(info);
		}
	}

	/** IAsyncInfo.Close() - only after GetResults (it frees the result). */
	static void close(Pointer op) {
		Pointer info = query(op, IID_IASYNCINFO);
		if (info != null) {
			call(info, ASYNC_CLOSE);
			release(info);
		}
	}

	/** Awaits an IAsyncOperation whose result is an interface pointer; releases the operation. Null on failure. */
	static Pointer awaitPtr(Pointer op, long timeoutMs, String what) {
		if (op == null) {
			return null;
		}
		try {
			if (!await(op, timeoutMs, what)) {
				return null;
			}
			PointerByReference out = new PointerByReference();
			int hr = call(op, ASYNC_GET_RESULTS, out);
			if (hr < 0 || out.getValue() == null) {
				MediaService.debug(what + ": GetResults " + (hr < 0 ? "failed 0x" + Integer.toHexString(hr) : "returned nothing"));
				return null;
			}
			return out.getValue();
		} finally {
			close(op);
			release(op);
		}
	}

	/** Awaits an IAsyncOperation&lt;bool&gt; (the Try* playback commands); releases it. */
	static boolean awaitBool(Pointer op, long timeoutMs, String what) {
		if (op == null) {
			return false;
		}
		try {
			if (!await(op, timeoutMs, what)) {
				return false;
			}
			Memory out = new Memory(4);
			out.setInt(0, 0);
			int hr = call(op, ASYNC_GET_RESULTS, out);
			if (hr < 0) {
				MediaService.debug(what + ": GetResults failed 0x" + Integer.toHexString(hr));
				return false;
			}
			return out.getByte(0) != 0;
		} finally {
			close(op);
			release(op);
		}
	}
}
