package com.wyz.lucene.compress.pfor;

import com.wyz.lucene.compress.common.AbstractIntRangeCodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NewPFDCodes extends AbstractIntRangeCodes {

	static final double  exceptionThresholdRate = 0.05;

	static final double  exceptionRate = 0.05;

	private static final int limitDataNum = 128;

	private S9 s9 = this.new S9();

	private static int[] basicMask = new int[]{
		0,
		1,
		( 1 << 2 )  -1,
		( 1 << 3 )  -1,
		( 1 << 4 )  -1,
		( 1 << 5 )  -1,
		( 1 << 6 )  -1,
		( 1 << 7 )  -1,
		( 1 << 8 )  -1,
		( 1 << 9 )  -1,
		( 1 << 10 ) -1,
		( 1 << 11 ) -1,
		( 1 << 12 ) -1,
		( 1 << 13 ) -1,
		( 1 << 14 ) -1,
		( 1 << 15 ) -1,
		( 1 << 16 ) -1,
		( 1 << 17 ) -1,
		( 1 << 18 ) -1,
		( 1 << 19 ) -1,
		( 1 << 20 ) -1,
		( 1 << 21 ) -1,
		( 1 << 22 ) -1,
		( 1 << 23 ) -1,
		( 1 << 24 ) -1,
		( 1 << 25 ) -1,
		( 1 << 26 ) -1,
		( 1 << 27 ) -1,
		( 1 << 28 ) -1,
		( 1 << 29 ) -1,
		( 1 << 30 ) -1,
		( 1 << 31 ) -1,
		( 1 << 32 ) -1

	};


	/**
	 * encode Integer Array to Compressed IntegerArray
	 *
	 * @param numbers
	 */
	@Override
	protected int[] innerEncode( int[] numbers ){
		//数组长额
		int dataNum		=	numbers.length;
		//需要多少个buffer去装，没128为一个buffer
		int bufferBlock =	( dataNum + limitDataNum - 1 ) / limitDataNum;

		int[][] frames = new int[ bufferBlock ][];
		for( int offset = 0, term = 0 ; term < bufferBlock ; offset += limitDataNum , term++ ){

			int diff	=	dataNum - offset;
			int length	=	diff <= limitDataNum ? diff : limitDataNum;

			int[] params = optimizedCompressBits( numbers, offset, length );
			int b				=	params[0];	// numFrameBits
			int exceptionNum	=	params[1];

			int[] exceptionList	= new int[exceptionNum];

			boolean lastFlag = false;
			if(term == bufferBlock-1)
				lastFlag = true;
			//执行压缩方法
			int[] frameData	= compress( length, b, offset, numbers, exceptionList, lastFlag );

			frames[term] = frameData;

		}

		return toBitFrame( dataNum, frames );
	}


	/**
	 * combinate frames
	 *
	 * @param frames
	 * @return
	 */
	private int[] toBitFrame( int dataNum, Object[] frames ){
		int[] prev = new int[]{dataNum};
		for( int i = 0 ; i < frames.length ; i++ ){
			int[] frame = ( (int[])frames[i] );
			int[] dest	= new int[ prev.length + frame.length];
			System.arraycopy(prev, 0, dest, 0, prev.length);
			System.arraycopy(frame, 0, dest, prev.length, frame.length);
			prev = dest;
		}
		return prev;
	}



	 /**
	  * compress integer numbers into frame
	  *
	  * @param length
	  * @param bitFrame
	  * @param offset	// offset for dataNum
	  * @param numbers
	  * @param exception
	  * @param lastFlag
	  * @return
	  */
	private int[] compress( int length, int bitFrame, int offset, int[] numbers, int[] exception, boolean lastFlag ){

		int pre		=  0;
		int max		=  1 << bitFrame;
		int[] code	=  new int[length];
		int[] miss	=  new int[length];

		// loop1: 找到异常码
		int j = 0;
		for( int i = 0 ; i < length ; i++ ){
			int val = numbers[ i + offset ];
			code[i] = val;
			miss[j] = i;
			if( max <= val ) j++;
		}

		if( exception.length == 0 )
			// 长度为0是代表没有异常码
			return transformToFrame( bitFrame, 0, code, exception, null, lastFlag );

		int[] exceptionOffset	=	new int[ exception.length - 1 ];

		//该处核心压缩逻辑，将异常位置右移异常位次，将其存到另外一个异常数组当中
		int cur = miss[ 0 ];
		exception[0] = code[cur] >> bitFrame ;
		code[cur] = code[cur] & basicMask[bitFrame];
		pre	= cur ;

		for( int i = 1 ; i < j ; i++ ){
			cur = miss[ i ];
			exception[i] = code[cur] >> bitFrame;
			code[cur] = code[cur] & basicMask[bitFrame];
			exceptionOffset[i-1] = cur - pre - 1;
			pre	= cur ;
		}

		int firstExceptionPos = miss[0] + 1;
		//System.out.println("firstExceptionPos : "+firstExceptionPos);
		return transformToFrame( bitFrame, firstExceptionPos, code, exception, exceptionOffset, lastFlag );

	}


	private static int headerSize = 1;



	/**
	 * 异常和正常值被压缩为位值。
	 *
	 * @param b
	 * @param firstExceptionPos : miss[0]+1 or 0
	 * @param code
	 * @param exception
	 * @return
	 */
	private int[] transformToFrame( int b, int firstExceptionPos, int[] code, int[] exception, int[] exceptionOffset, boolean lastFlag ){
		//头 + （数组长度 * 异常值长度 + 31） / 32  计算出异常值的int偏移量
		int exceptionIntOffset = headerSize + ( code.length * b + 31 ) / 32;

		if( firstExceptionPos == 0 ){

			int[] frame	= new int[exceptionIntOffset];
			frame[0] = makeHeader( b, firstExceptionPos, code, 0, 0, lastFlag );
			for( int i = 0 ; i < code.length ; i++ ){
				int val = code[i];
				encodeCompressedValue( i, val, b, frame );	// normal encoding
			}
			return frame;

		}


		//计算异常范围
		int exceptionOffsetNum = exceptionOffset.length;
		int exceptionNum = exception.length;

		int[] exceptionDatum = new int[ exceptionOffsetNum + exceptionNum ];
		for( int i = 0 ; i < exceptionNum ; i++ )
			exceptionDatum[ 2 * i ] = exception[i];
		for( int i = 0 ; i < exceptionOffsetNum ; i++ )
			exceptionDatum[ 2 * i + 1 ] = exceptionOffset[i];

		int[] compressedExceptionDatum = s9.encodeWithoutDataNumHeader( exceptionDatum );

		int exceptionRange = compressedExceptionDatum.length ;
		int intDataSize	= exceptionIntOffset + compressedExceptionDatum.length ;

		int[] frame	= new int[intDataSize];


		// 1: make header
		frame[0] = makeHeader( b, firstExceptionPos, code, exceptionNum, exceptionRange, lastFlag );

		// 2: make encoded value
		for( int i = 0 ; i < code.length ; i++ ){
			int val = code[i];
			encodeCompressedValue( i, val, b, frame );	// normal encoding
		}

		// 3: make exception value
		encodeExceptionValues( exceptionIntOffset, compressedExceptionDatum, frame );
		return frame;

	}



	/**
	 * encode exception values
	 *
	 * @param exceptionIntOffset : ( header + compressed code ) int-length
	 * @param compressedExceptionDatum
	 * @param frame
	 */
	private void encodeExceptionValues( int exceptionIntOffset, int[] compressedExceptionDatum, int[] frame ){

		int offset = exceptionIntOffset;
		for( int i = 0 ; i < compressedExceptionDatum.length ; i++ )
			frame[offset++] = compressedExceptionDatum[i];

	}



	/**
	 * encode normal value
	 *
	 * @param i
	 * @param val
	 * @param b
	 * @param frame
	 */
	private void encodeCompressedValue( int i, int val, int b, int[] frame ){

		int _val = val;
		int totalBitCount = b * i;
		int intPos	 = totalBitCount >>> 5;
		int firstBit = totalBitCount % 32;
		int endBit = firstBit + b;

		int baseMask = basicMask[b];
		int mask 	 = 0;

		mask = ~( baseMask << firstBit );
		_val = val << firstBit;

		frame[ intPos + headerSize ] = frame[ intPos + headerSize ]
		                                      & mask
		                                      | _val;

		// over bit-width of integer
		if( 32 < endBit ){
			int shiftBit = b - ( endBit - 32 );
			mask = ~( baseMask >>> shiftBit );
			_val = val >>> shiftBit;
			frame[ intPos + headerSize + 1] = frame[ intPos + headerSize + 1]
			                                         & mask
			                                         | _val;
		}

	}



	/**
	 * Header is consist of 1 byte and the construction is as follow
	 *
	 * 7bit : dataNum - 1
	 * 8bit : first exceptionPos
	 * 5bit : numFramebit -1
	 * 11bit : exception byte range
	 * 1bit : has next frame or not
	 *
	 * @param b
	 * @param firstExceptionPos
	 * @param code
	 * @return
	 */
	private int makeHeader( int b, int firstExceptionPos, int[] code, int exceptionNum,
							int exceptionIntRange, boolean lastFlag ){

		int dataNum   = code.length -1 ;
		int lastOrNot = lastFlag ? 1 : 0;
        return dataNum << 25
        		| firstExceptionPos << 17
        		| ( b - 1 ) << 12
        		| exceptionIntRange << 1
        		| lastOrNot;

	}


	/**
	 * 计算最有比特数，按照10%异常值的预测估计
	 *
	 * @param numbers
	 * @param offset
	 * @param length : 数据长度
	 * @return 2 value int
	 * 			( bitFrame, exceptionNum )
	 */
	private int[] optimizedCompressBits( int[] numbers, int offset, int length ){

		int[] copy = new int[length];
		System.arraycopy(numbers, offset, copy , 0, length);
		Arrays.sort(copy);

		int maxValue	=	copy[ length - 1 ];
		if ( maxValue <= 1 ) return new int[]{ 1, 0 }; // bitFrame, exceptionNum, exceptionCode :
		//最大的一定是异常码，8位一个字节，判断是几个字节0-2^8=0 2^8-2^16=1 >2^16=2
		int exceptionCode = ( maxValue < ( 1 << 8 ) ) ? 0 : (maxValue < (1 << 16 )) ? 1 : 2;
		//异常码占用的字节数
		int bytesPerException = 1 << exceptionCode;
		int frameBits	=	1;
		//计算所需字节数
		int bytesForFrame = (length * frameBits + 7 ) / 8; // cut up byte

		// 首先假设所有的数据都是异常值
		int totalBytes		=	bytesForFrame + length * bytesPerException; // excluding the header.
		int bestBytes		=	totalBytes;
		int bestFrameBits	=	frameBits;
		int bestExceptions	=	length;

		for (int i = 0; i < length; i++) {
			// 确定比特数，copy[i]就不再是异常数字了,frameBits为该copy[i]的占用比特数
			while ( copy[i] >= (1 << frameBits) ) {
				//如果异常值大于30比特了，那么以后的循环也无需进行了
				//总长度减去之前小于30比特不异常的就是总异常值
				if ( frameBits == 30 ) {
					return rebuild( copy, bestFrameBits,  length - i - 1 );
				}
				//一共需要的比特数，每循环一次证明总长度需要增加1
				// 由于是从小到大排序的，所以下次的肯定大于前一次，故无需从1循环
				++frameBits;
				// 使用新的字节数进行赋值
				int newBytesForFrame = (length * frameBits + 7 ) / 8;
				totalBytes += newBytesForFrame - bytesForFrame;
				bytesForFrame = newBytesForFrame;
			}
			totalBytes -= bytesPerException; // 处理完一个值就在总数据中减去定义的异常值
			if ( totalBytes <= bestBytes ) { // <= : 当比特高的时候期望一个更少的异常值
				bestBytes		=	totalBytes;
				bestFrameBits	=	frameBits;
				bestExceptions	=	length - i - 1;
			}

		}
		return rebuild( copy, bestFrameBits,  bestExceptions );
	}



	private int[] rebuild( int[] copy, int bestFrameBits, int bestExceptions ){

		if( bestExceptions <= limitDataNum * exceptionThresholdRate  )
			return new int[]{bestFrameBits, bestExceptions};

		int length = copy.length;
		int maxValue	=	copy[ length - 1 ];
		if ( maxValue <= 1 ) return new int[]{ 1, 0 }; // bitFrame, exceptionNum, exceptionCode :

		int searchPos	=	(int) Math.floor( length * ( 1- exceptionRate ) );
		searchPos = searchPos == 0 ? 1 : searchPos;

		int currentVal	=	copy[ searchPos - 1 ];

		int i   = 1;
		int max = 0 ;
		for( ; i < 32 ; i++ ){
			max = basicMask[i];
			if( currentVal <= max )
				break;
		}
		int candidateBit = i;

		// search exception num
		for( int j = 0 ; j < length ; j++ ){
			if( max < copy[j] )
				return new int[]{ candidateBit, length - j };
		}

		return new int[]{ candidateBit, 0 };

	}


	@Override
	public int[] decode ( int[] encodedValue, boolean useGapList  ){
		int totalDataNum = encodedValue[0];
		return  fastDecodeFrame( 1, encodedValue , 0, new int[totalDataNum], useGapList );
	}


	private int[] fastDecodeFrame( int headerPos, int[] encodedValue, int decodeOffset, int[] decode, boolean useGapList ){


		/****************************************************************
		 * decode header value
		 * header component is as follow
		 *
		 * 7bit : dataNum - 1
		 * 8bit : first exceptionPos
		 * 5bit : numFramebit -1
		 * 11bit : exception byte range
		 * 1bit : has next frame or not
		 *
		 *****************************************************************/

		int headerValue = encodedValue[headerPos];

		int dataNum 			= 	( headerValue >>> 25 ) + 1 ;
		int firstExceptionPos   =	( headerValue << 7 ) >>> 24 ; 			// miss[0] + 1 or 0
		int numFrameBit			=	( ( headerValue << 15) >>> 27 ) + 1 ;		// 1 < numFramebit < 32
		int exceptionIntRange	=	( headerValue << 20 ) >>> 21 ;
		int lastFlag			=	( headerValue << 31 ) >>> 31 ;

		/***************************************************************/

		// first loop
		int encodeOffset = headerPos + headerSize ;

		int intOffsetForExceptionRange;
		switch( numFrameBit ){
			case 1  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor1Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 2  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor2Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 3  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor3Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 4  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor4Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 5  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor5Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 6  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor6Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 7  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor7Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 8  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor8Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 9  : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor9Bit ( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 10 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor10Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 11 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor11Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 12 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor12Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 13 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor13Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 14 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor14Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 15 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor15Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 16 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor16Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 17 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor17Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 18 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor18Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 19 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor19Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 20 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor20Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 21 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor21Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 22 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor22Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 23 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor23Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 24 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor24Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 25 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor25Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 26 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor26Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 27 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor27Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 28 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor28Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 29 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor29Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 30 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor30Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 31 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor31Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			case 32 : intOffsetForExceptionRange = PForDeltaDecompress.fastDeCompressFor32Bit( encodeOffset, encodedValue, dataNum, decodeOffset, decode ); break;
			default : throw new RuntimeException("numFramBit is too high ! " + numFrameBit);
		}



		//exception loop
		if( firstExceptionPos != 0 )
			s9.decode( encodedValue, intOffsetForExceptionRange, exceptionIntRange, decode,  firstExceptionPos - 1 );


		if( lastFlag == 0 ){
			int nextFrameIntPos = intOffsetForExceptionRange + exceptionIntRange;
			decodeOffset += dataNum;
			return fastDecodeFrame( nextFrameIntPos, encodedValue, decodeOffset, decode, useGapList );
		}
		else{

			if(useGapList)
				for(int i = 1 ; i < decode.length ; i++ )
					decode[i] += decode[i-1];
			return decode;

		}

	}


	class S9 {

		private int bitLength[]	=	{ 1, 2, 3, 4, 5, 7, 9, 14, 28 };

		private int codeNum[]	=	{ 28, 14, 9, 7, 5, 4, 3, 2, 1 };


		public int[] encodeWithoutDataNumHeader( int numbers[] ){

			List<Integer> resultList = new ArrayList<Integer>();

			int currentPos = 0;
			while( currentPos < numbers.length ){

				for( int selector = 0 ; selector < 9 ; selector++ ){

					int res = 0;
					int compressedNum = codeNum[selector];
					if( numbers.length <= currentPos + compressedNum -1 )
						continue;
					int b = bitLength[selector];
					int max = 1 << b ;
					int i = 0;
					for( ; i < compressedNum ; i++ )
						if( max <= numbers[currentPos + i] )
							break;
						else
							res = ( res << b ) + numbers[currentPos + i];

					if( i == compressedNum ) {
						res |= selector << 28;
						resultList.add(res);
						currentPos += compressedNum;
						break;
					}

				}

			}

			int resultNum = resultList.size();
			int[] resultArray = new int[ resultNum ];
			for( int i = 0 ; i < resultNum ; i++ )
				resultArray[i] = resultList.get(i);
			return resultArray;

		}



		public void decode( int encodedValue[], int offset, int length, int[] decode, int firstExceptionPos ){

			int correntPos = firstExceptionPos;
			int head = 0;
			for( int i = 0 ; i < length ; i++ ){

				int val = encodedValue[ offset + i ] ;
				int header = ( val >>> 28 ) + head;

				switch( header ){

					case 0 : { //code num : 28, bitwidth : 1
						decode[ correntPos ]  = ( val << 4  ) >>> 31 ;
						correntPos += ( ( val << 5  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 6  ) >>> 31 ;
						correntPos += ( ( val << 7  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 8  ) >>> 31 ;
						correntPos += ( ( val << 9  ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 10 ) >>> 31 ;
						correntPos += ( ( val << 11 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 12 ) >>> 31 ;
						correntPos += ( ( val << 13 ) >>> 31 ) + 1 ; //10
						decode[ correntPos ]  = ( val << 14 ) >>> 31 ;
						correntPos += ( ( val << 15 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 16 ) >>> 31 ;
						correntPos += ( ( val << 17 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 18 ) >>> 31 ;
						correntPos += ( ( val << 19 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 20 ) >>> 31 ;
						correntPos += ( ( val << 21 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 22 ) >>> 31 ;
						correntPos += ( ( val << 23 ) >>> 31 ) + 1 ; //20
						decode[ correntPos ]  = ( val << 24 ) >>> 31 ;
						correntPos += ( ( val << 25 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 26 ) >>> 31 ;
						correntPos += ( ( val << 27 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 28 ) >>> 31 ;
						correntPos += ( ( val << 29 ) >>> 31 ) + 1 ;
						decode[ correntPos ]  = ( val << 30 ) >>> 31 ;
						correntPos += ( ( val << 31 ) >>> 31 ) + 1 ;
						head = 0;
						break;
					}
					case 1 : { //code num : 14, bitwidth : 2
						decode[ correntPos ]  = ( val << 4  ) >>> 30 ;
						correntPos += ( ( val << 6  ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 8  ) >>> 30 ;
						correntPos += ( ( val << 10 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 12 ) >>> 30 ;
						correntPos += ( ( val << 14 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 16 ) >>> 30 ;
						correntPos += ( ( val << 18 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 20 ) >>> 30 ;
						correntPos += ( ( val << 22 ) >>> 30 ) + 1 ; //10
						decode[ correntPos ]  = ( val << 24 ) >>> 30 ;
						correntPos += ( ( val << 26 ) >>> 30 ) + 1 ;
						decode[ correntPos ]  = ( val << 28 ) >>> 30 ;
						correntPos += ( ( val << 30 ) >>> 30 ) + 1 ;
						head = 0;
						break;
					}
					case 2 : { //code num : 9, bitwidth : 3
						decode[ correntPos ] = ( val << 5  ) >>> 29 ;
						correntPos += ( ( val << 8  ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 11 ) >>> 29 ;
						correntPos += ( ( val << 14 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 17 ) >>> 29 ;
						correntPos += ( ( val << 20 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 23 ) >>> 29 ;
						correntPos += ( ( val << 26 ) >>> 29 ) + 1 ;
						decode[ correntPos ] = ( val << 29 ) >>> 29 ;
						head = 16;
						break;
					}
					case 3 : { //code num : 7, bitwidth : 4
						decode[ correntPos ] = ( val << 4  ) >>> 28 ;
						correntPos += ( ( val << 8  ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 12 ) >>> 28 ;
						correntPos += ( ( val << 16 ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 20 ) >>> 28 ;
						correntPos += ( ( val << 24 ) >>> 28 ) + 1 ;
						decode[ correntPos ] = ( val << 28 ) >>> 28 ;
						head = 16;
						break;
					}
					case 4 : { //code num : 5, bitwidth : 5
						decode[ correntPos ] = ( val << 7  ) >>> 27 ;
						correntPos += ( ( val << 12 ) >>> 27 ) + 1 ;
						decode[ correntPos ] = ( val << 17 ) >>> 27 ;
						correntPos += ( ( val << 22 ) >>> 27 ) + 1 ;
						decode[ correntPos ] = ( val << 27 ) >>> 27 ;
						head = 16;
						break;
					}
					case 5 : { //code num : 4, bitwidth : 7
						decode[ correntPos ] = ( val << 4  ) >>> 25 ;
						correntPos += ( ( val << 11 ) >>> 25 ) + 1 ;
						decode[ correntPos ] = ( val << 18 ) >>> 25 ;
						correntPos += ( ( val << 25 ) >>> 25 ) + 1 ;
						head = 0;
						break;
					}
					case 6 : { //code num : 3, bitwidth : 9
						decode[ correntPos ] = ( val << 5  ) >>> 23 ;
						correntPos += ( ( val << 14 ) >>> 23 ) + 1 ;
						decode[ correntPos ] = ( val << 23 ) >>> 23 ;
						head = 16;
						break;
					}
					case 7 : { //code num : 2, bitwidth : 14
						decode[ correntPos ] = ( val << 4  ) >>> 18 ;
						correntPos += ( ( val << 18 ) >>> 18 ) +1 ;
						head = 0;
						break;
					}
					case 8 : { //code num : 1, bitwidth : 28
						decode[ correntPos ] = ( val << 4 ) >>> 4;
						head = 16;
						break;
					}

					case 16 : { //code num : 28, bitwidth : 1
						correntPos += ( val << 4  ) >>> 31 ;
						decode[ correntPos ] = ( val << 5  ) >>> 31 ;
						correntPos += ( val << 6  ) >>> 31 ;
						decode[ correntPos ] = ( val << 7  ) >>> 31 ;
						correntPos += ( val << 8  ) >>> 31 ;
						decode[ correntPos ] = ( val << 9  ) >>> 31 ;
						correntPos += ( val << 10 ) >>> 31 ;
						decode[ correntPos ] = ( val << 11 ) >>> 31 ;
						correntPos += ( val << 12 ) >>> 31 ;
						decode[ correntPos ] = ( val << 13 ) >>> 31 ; //10
						correntPos += ( val << 14 ) >>> 31 ;
						decode[ correntPos ] = ( val << 15 ) >>> 31 ;
						correntPos += ( val << 16 ) >>> 31 ;
						decode[ correntPos ] = ( val << 17 ) >>> 31 ;
						correntPos += ( val << 18 ) >>> 31 ;
						decode[ correntPos ] = ( val << 19 ) >>> 31 ;
						correntPos += ( val << 20 ) >>> 31 ;
						decode[ correntPos ] = ( val << 21 ) >>> 31 ;
						correntPos += ( val << 22 ) >>> 31 ;
						decode[ correntPos ] = ( val << 23 ) >>> 31 ; //20
						correntPos += ( val << 24 ) >>> 31 ;
						decode[ correntPos ] = ( val << 25 ) >>> 31 ;
						correntPos += ( val << 26 ) >>> 31 ;
						decode[ correntPos ] = ( val << 27 ) >>> 31 ;
						correntPos += ( val << 28 ) >>> 31 ;
						decode[ correntPos ] = ( val << 29 ) >>> 31 ;
						correntPos += ( val << 30 ) >>> 31 ;
						decode[ correntPos ] = ( val << 31 ) >>> 31 ;
						head = 16;
						break;
					}
					case 17 : { //code num : 14, bitwidth : 2
						correntPos += ( val << 4  ) >>> 30 ;
						decode[ correntPos ] = ( val << 6  ) >>> 30 ;
						correntPos += ( val << 8  ) >>> 30 ;
						decode[ correntPos ] = ( val << 10 ) >>> 30 ;
						correntPos += ( val << 12 ) >>> 30 ;
						decode[ correntPos ] = ( val << 14 ) >>> 30 ;
						correntPos += ( val << 16 ) >>> 30 ;
						decode[ correntPos ] = ( val << 18 ) >>> 30 ;
						correntPos += ( val << 20 ) >>> 30 ;
						decode[ correntPos ] = ( val << 22 ) >>> 30 ; //10
						correntPos += ( val << 24 ) >>> 30 ;
						decode[ correntPos ] = ( val << 26 ) >>> 30 ;
						correntPos += ( val << 28 ) >>> 30 ;
						decode[ correntPos ] = ( val << 30 ) >>> 30 ;
						head = 16;
						break;
					}
					case 18 : { //code num : 9, bitwidth : 3
						correntPos += ( val << 5  ) >>> 29 ;
						decode[ correntPos ] = ( val << 8  ) >>> 29 ;
						correntPos += ( val << 11 ) >>> 29 ;
						decode[ correntPos ] = ( val << 14 ) >>> 29 ;
						correntPos += ( val << 17 ) >>> 29 ;
						decode[ correntPos ] = ( val << 20 ) >>> 29 ;
						correntPos += ( val << 23 ) >>> 29 ;
						decode[ correntPos ] = ( val << 26 ) >>> 29 ;
						correntPos += ( val << 29 ) >>> 29 ;
						head = 0;
						break;
					}
					case 19 : { //code num : 7, bitwidth : 4
						correntPos += ( val << 4  ) >>> 28 ;
						decode[ correntPos ] = ( val << 8  ) >>> 28 ;
						correntPos += ( val << 12 ) >>> 28 ;
						decode[ correntPos ] = ( val << 16 ) >>> 28 ;
						correntPos += ( val << 20 ) >>> 28 ;
						decode[ correntPos ] = ( val << 24 ) >>> 28 ;
						correntPos += ( val << 28 ) >>> 28 ;
						head = 0;
						break;
					}
					case 20 : { //code num : 5, bitwidth : 5
						correntPos += ( val << 7  ) >>> 27 ;
						decode[ correntPos ] = ( val << 12 ) >>> 27 ;
						correntPos += ( val << 17 ) >>> 27 ;
						decode[ correntPos ] = ( val << 22 ) >>> 27 ;
						correntPos += ( val << 27 ) >>> 27 ;
						head = 0;
						break;
					}
					case 21 : { //code num : 4, bitwidth : 7
						correntPos += ( val << 4  ) >>> 25 ;
						decode[ correntPos ] = ( val << 11 ) >>> 25 ;
						correntPos += ( val << 18 ) >>> 25 ;
						decode[ correntPos ] = ( val << 25 ) >>> 25 ;
						head = 16;
						break;
					}
					case 22 : { //code num : 3, bitwidth : 9
						correntPos += ( val << 5  ) >>> 23 ;
						decode[ correntPos ] = ( val << 14 ) >>> 23 ;
						correntPos += ( val << 23 ) >>> 23 ;
						head = 0;
						break;
					}
					case 23 : { //code num : 2, bitwidth : 14
						correntPos += ( val << 4  ) >>> 18 ;
						decode[ correntPos ] = ( val << 18 ) >>> 18 ;
						head = 16;
						break;
					}
					case 24 : { //code num : 1, bitwidth : 28
						correntPos += ( val << 4 ) >>> 4;
						head = 0;
						break;
					}
					default : throw new IllegalArgumentException();

				}
			}
		}

	}

}