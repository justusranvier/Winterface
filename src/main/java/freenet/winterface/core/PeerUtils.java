package freenet.winterface.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.wicket.markup.html.form.upload.FileUpload;

import freenet.clients.http.ConnectionsToadlet.PeerAdditionReturnCodes;
import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.node.DarknetPeerNode;
import freenet.node.DarknetPeerNode.FRIEND_TRUST;
import freenet.node.DarknetPeerNode.FRIEND_VISIBILITY;
import freenet.node.FSParseException;
import freenet.node.Node;
import freenet.node.PeerNode;
import freenet.support.SimpleFieldSet;

/**
 * Util to parse node references and add it to the {@link Node}
 * 
 * @author pausb
 * @see Node#createNewDarknetNode(SimpleFieldSet, FRIEND_TRUST,
 *      FRIEND_VISIBILITY)
 * @see Node#createNewOpennetNode(SimpleFieldSet)
 */
public class PeerUtils {

	/** New line char */
	public final static char NEW_LINE_CHAR = '\n';
	/** End marker for node refs */
	public final static String REF_END_MARKER = "End";

	/**
	 * This slightly scary looking regex chops any extra characters off the
	 * beginning or ends of lines and removes extra line breaks
	 */
	private final static String CLEANER_REGEX = ".*?((?:[\\w,\\.]+\\=[^\r\n]+?)|(?:End))[ \\t]*(?:\\r?\\n)+";
	/** Replaces the extra characters of {@link #CLEANER_REGEX} */
	private final static String CLEANER_REPLACE = "$1\n";

	/** Log4j logger */
	private final static Logger logger = Logger.getLogger(PeerUtils.class);

	/**
	 * Create node reference from given {@link URL} address.
	 * 
	 * @param urlText
	 *            {@link URL} address
	 * @return noe reference as {@link String}
	 * @throws IOException
	 */
	public static String buildRefsFromUrl(String urlText) throws IOException {
		if (urlText == null || !(urlText.length() > 0)) {
			throw new IllegalArgumentException("URL string may not be null or empty");
		}
		StringBuilder result = new StringBuilder(1024);
		URL url = new URL(urlText);
		BufferedReader reader = null;
		try {
			URLConnection uc = url.openConnection();
			reader = new BufferedReader(new InputStreamReader(uc.getInputStream(), uc.getContentType()));
			String line;
			while ((line = reader.readLine()) != null) {
				result.append(line).append(NEW_LINE_CHAR);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return buildRefsFromString(result.toString());
	}

	/**
	 * Create node reference from given {@link FileUpload}.
	 * 
	 * @param file
	 *            uploaded node reference
	 * @return node reference as {@link String}
	 */
	public static String buildRefsFromFile(FileUpload file) {
		return buildRefsFromString(new String(file.getBytes()));
	}

	/**
	 * Creates node reference from given {@link String}
	 * 
	 * @param ref
	 *            node reference as {@link String}
	 * @return cleaned up node reference
	 * @see #CLEANER_REGEX
	 * @see #CLEANER_REPLACE
	 */
	public static String buildRefsFromString(String ref) {
		return ref.replaceAll(CLEANER_REGEX, CLEANER_REPLACE).trim();
	}

	/**
	 * Splits a list of node references into single node references.
	 * <p>
	 * Note that only the last node reference in the list would contain the
	 * <b>End</b> marker.
	 * </p>
	 * 
	 * @param refs
	 *            a {@link String} containing new-line-separated list of node
	 *            refs
	 * @return single node refs
	 * @see #REF_END_MARKER
	 */
	public static String[] splitRefs(String refs) {
		StringBuilder b = new StringBuilder(refs.trim());
		int idx;
		while ((idx = b.indexOf("\r\n")) > -1) {
			b.deleteCharAt(idx);
		}
		while ((idx = b.indexOf("\r")) > -1) {
			// Mac's just use \r
			b.setCharAt(idx, NEW_LINE_CHAR);
		}
		return b.toString().split(NEW_LINE_CHAR + REF_END_MARKER + NEW_LINE_CHAR);
	}

	/**
	 * Splits lines of a node ref into a {@link String} array.
	 * <p>
	 * If <b>End</b> marker is reached the method stops reading the ref.
	 * Otherwise all lines are read and <b>End</b> is added as the last line.
	 * </p>
	 * 
	 * @param ref
	 *            node ref
	 * @return node ref split into lines
	 * @see #REF_END_MARKER
	 */
	public static String[] splitRef(String ref) {
		String[] lines = ref.split("\n");
		List<String> result = new ArrayList<String>();
		for (String line : lines) {
			if (line.equals(REF_END_MARKER))
				break;
			result.add(line);
		}
		result.add(REF_END_MARKER);
		return result.toArray(new String[result.size()]);
	}

	/**
	 * Adds a new Openenet node
	 * 
	 * @param node
	 *            Freenet node to add peer to
	 * @param ref
	 *            peer node reference
	 * @return add status
	 * @see #addNewDarknetNode(Node, String, String, FRIEND_TRUST,
	 *      FRIEND_VISIBILITY)
	 */
	public static PeerAdditionReturnCodes addNewOpennetNode(Node node, String[] ref) {
		return addNewDarknetNode(node, ref, null, null, null);
	}

	/**
	 * Adds a new Darknet node
	 * 
	 * @param node
	 *            Freenet node to add peer to
	 * @param ref
	 *            peer node reference
	 * @param comment
	 *            comment on peer
	 * @param trust
	 *            trust level
	 * @param visibility
	 *            visibility of peer to other friends
	 * @return add status
	 * @see #addNewOpennetNode(Node, String)
	 */
	public static PeerAdditionReturnCodes addNewDarknetNode(Node node, String[] ref, String comment, FRIEND_TRUST trust, FRIEND_VISIBILITY visibility) {
		SimpleFieldSet fs;
		try {
			fs = new SimpleFieldSet(ref, false, true);
			if (!fs.getEndMarker().endsWith(REF_END_MARKER)) {
				logger.error("Trying to add noderef with end marker \"" + fs.getEndMarker() + "\"");
				return PeerAdditionReturnCodes.WRONG_ENCODING;
			}
			fs.setEndMarker(REF_END_MARKER); // It's always End ; the regex
												// above doesn't always grok
												// this
		} catch (IOException e) {
			logger.error("Internal error", e);
			return PeerAdditionReturnCodes.INTERNAL_ERROR;
		}
		PeerNode pn;
		try {
			if (trust == null) {
				pn = node.createNewOpennetNode(fs);
			} else {
				pn = node.createNewDarknetNode(fs, trust, visibility);
				if (comment != null)
					((DarknetPeerNode) pn).setPrivateDarknetCommentNote(comment);
			}
		} catch (FSParseException e) {
			logger.error("Cant parse", e);
			return PeerAdditionReturnCodes.CANT_PARSE;
		} catch (PeerParseException e) {
			logger.error("Cant parse", e);
			return PeerAdditionReturnCodes.CANT_PARSE;
		} catch (ReferenceSignatureVerificationException e) {
			logger.error("Invalid signature", e);
			return PeerAdditionReturnCodes.INVALID_SIGNATURE;
		} catch (Throwable t) {
			logger.error("Internal error", t);
			return PeerAdditionReturnCodes.INTERNAL_ERROR;
		}
		if (Arrays.equals(pn.getIdentity(), node.getDarknetIdentity())) {
			return PeerAdditionReturnCodes.TRY_TO_ADD_SELF;
		}
		if (node.addPeerConnection(pn)) {
			return PeerAdditionReturnCodes.ALREADY_IN_REFERENCE;
		}
		return PeerAdditionReturnCodes.OK;
	}
}
