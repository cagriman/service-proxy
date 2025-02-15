/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.*;
import java.util.Arrays;

import org.junit.jupiter.api.*;

import com.predic8.membrane.core.util.EndOfStreamException;

public class RequestTest {

	private static Request reqPost = new Request();

	private static Request reqChunked = new Request();

	private InputStream inPost;

	private InputStream inChunked;

	private ByteArrayOutputStream tempOut;

	private InputStream tempIn;

	@BeforeEach
	public void setUp() throws Exception {
		inPost = RequestTest.class.getClassLoader().getResourceAsStream("request-post.msg");
		inChunked = RequestTest.class.getClassLoader().getResourceAsStream("request-chunked-soap.msg");
	}

	@AfterEach
	public void tearDown() throws Exception {

		if (inPost != null) {
			inPost.close();
		}

		if (inChunked != null) {
			inChunked.close();
		}

		if (tempIn != null) {
			tempIn.close();
		}

		if (tempOut != null) {
			tempOut.close();
		}

	}


	@Test
	public void testParseStartLineChunked() throws IOException, EndOfStreamException {
		reqChunked.parseStartLine(inChunked);
		assertTrue(reqChunked.isPOSTRequest());
		assertEquals("/axis2/services/BLZService", reqChunked.getUri());
		assertEquals("1.1", reqChunked.getVersion());
	}

	@Test
	public void testReadChunked() throws Exception {
		reqChunked.read(inChunked, true);
		assertNotNull(reqChunked.getBodyAsStream());
	}

	@Test
	public void testReadPost() throws Exception {
		reqPost.read(inPost, true);
		assertEquals(Request.METHOD_POST, reqPost.getMethod());
		assertEquals("/operation/call", reqPost.getUri());
		assertNotNull(reqPost.getBody());

		assertEquals(168, reqPost.getBody().getLength());
	}

	@Test
	public void testWritePost() throws Exception {
		reqPost.read(inPost, true);

		tempOut = new ByteArrayOutputStream();
		reqPost.write(tempOut, true);

		tempIn = new ByteArrayInputStream(tempOut.toByteArray());

		Request reqTemp = new Request();
		reqTemp.read(tempIn, true);

		assertEquals(reqPost.getUri(), reqTemp.getUri());
		assertEquals(reqPost.getMethod(), reqTemp.getMethod());

		assertArrayEquals(reqPost.getBody().getContent(), reqTemp.getBody().getContent());
		assertArrayEquals(reqPost.getBody().getRaw(), reqTemp.getBody().getRaw());
	}

	@Test
	public void testIsHTTP11() {
		assertTrue(reqPost.isHTTP11());
	}

	@Test
	public void testIsHTTP11Chunked() {
		assertTrue(reqChunked.isHTTP11());
	}

	@Test
	public void testIsKeepAlive() {
		assertTrue(reqPost.isKeepAlive());
	}

	@Test
	public void testIsKeepAliveChunked() {
		assertTrue(reqChunked.isKeepAlive());
	}

	@Test
	public void isEmpty() throws IOException, URISyntaxException {
		assertTrue(new Request.Builder().body("").build().isBodyEmpty());
		assertTrue(new Request.Builder().body("".getBytes(UTF_8)).build().isBodyEmpty());
		assertTrue(new Request.Builder().get("http://predic8.de").build().isBodyEmpty());
	}

	@Test
	public void isNotEmpty() throws IOException {
		assertFalse(new Request.Builder().body("ABC").build().isBodyEmpty());
	}
}