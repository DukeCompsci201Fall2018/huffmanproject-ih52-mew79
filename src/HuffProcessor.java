import java.util.PriorityQueue;

// Ian Hanus and Mary Wang
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT,  HUFF_TREE);;
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		out.close();
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] freqArray = new int[ALPH_SIZE + 1];
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) {
				break;
			}
			freqArray[PSEUDO_EOF] = 1;
			if(bits == PSEUDO_EOF) {
				freqArray[PSEUDO_EOF] = 1;
			}
			freqArray[bits] = freqArray[bits] + 1;
		}
		return freqArray;
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int k = 0; k < counts.length; k++) {
			if(counts[k] > 0) {
				pq.add(new HuffNode(k, counts[k], null, null));
			}
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		String leftPlaceholder = path + "0";
		codingHelper(root.myLeft, leftPlaceholder, encodings);
		String rightPlaceholder = path + "1";
		codingHelper(root.myRight, rightPlaceholder, encodings);
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft != null || root.myRight != null) {
			out.writeBits(1, 0);
			HuffNode left = root.myLeft;
			HuffNode right = root.myRight;
			writeHeader(left, out);
			writeHeader(right, out);
		}
		if(root.myLeft == null && root.myRight == null) {
			out.writeBits(1,  1);
			out.writeBits(BITS_PER_WORD+1, root.myValue);
		}
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int code = in.readBits(BITS_PER_WORD);
			if(code == -1) {
				break;
			}
			String c = codings[code];
			if(myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("Huffman encoding is %s \n", c);
			}
			out.writeBits(c.length(),  Integer.parseInt(c, 2));
		}
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("Illegal header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();

	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if(bit == -1) {
			throw new HuffException("You can't do that");
		}
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		else {
			int value = in.readBits(9);
			return new HuffNode(value, 0, null, null);
		}
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while(true) {
			int bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("Can't do that");
			}
			else {
				if(bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				
				if(current.myRight == null && current.myLeft == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}