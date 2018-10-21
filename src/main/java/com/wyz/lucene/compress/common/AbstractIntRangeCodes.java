package com.wyz.lucene.compress.common;

import java.util.Arrays;

public abstract class AbstractIntRangeCodes {


	public int[] encode( int[] numbers, boolean useArraySort, boolean useGapList){

		if(useArraySort)
			Arrays.sort( numbers );


		if(useGapList)
			numbers = toGapArray( numbers );

		return innerEncode(numbers);

	}

	protected static int[] toGapArray( int[] numbers ){

		int prev  = numbers[0];

		for( int i =1 ; i < numbers.length ; i++ ){
			int tmp = numbers[i];
			numbers[i] = numbers[i] - prev;
			prev = tmp;
		}
		return numbers;

	}

	protected abstract int[] innerEncode( int[] gappedNumbers );

	public  abstract int[] decode ( int[] encodedValue , boolean useGapList );

}