package org.apache.lucene.index;

import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.BufferedIndexInput;

/**
 * 采用二分查找法进行优化
 */

final class MyBinarySearchReader {
    private Directory directory;
    private String segment;
    private FieldInfos fieldInfos;

    private ThreadLocal enumerators = new ThreadLocal();
    private SegmentTermEnum origEnum;
    private long size;

    private Term[] indexTerms = null;
    private TermInfo[] indexInfos;
    private long[] indexPointers;

    private SegmentTermEnum indexEnum;

    private int indexDivisor = 1;
    private int totalIndexInterval;

    MyBinarySearchReader(Directory dir, String seg, FieldInfos fis)
            throws CorruptIndexException, IOException {
        this(dir, seg, fis, BufferedIndexInput.BUFFER_SIZE);
    }

    MyBinarySearchReader(Directory dir, String seg, FieldInfos fis, int readBufferSize)
            throws CorruptIndexException, IOException {
        boolean success = false;

        try {
            directory = dir;
            segment = seg;
            fieldInfos = fis;

            origEnum = new SegmentTermEnum(directory.openInput(segment + ".tis",
                    readBufferSize), fieldInfos, false);
            size = origEnum.size;
            totalIndexInterval = origEnum.indexInterval;

            indexEnum = new SegmentTermEnum(directory.openInput(segment + ".tii",
                    readBufferSize), fieldInfos, true);

            success = true;
        } finally {
            if (!success) {
                close();
            }
        }
    }

    public int getSkipInterval() {
        return origEnum.skipInterval;
    }

    public int getMaxSkipLevels() {
        return origEnum.maxSkipLevels;
    }

    /**
     * 热点数据加载
     * 配置indexDivisor，默认使用1（每间隔一个加载到内存当中）
     * 可以使用>1的其他数字增加缓存在内存中的元素数量，但是加载过程会增加延时，同时会增加查询速度
     * @param indexDivisor
     * @throws IllegalStateException
     */

    public void setIndexDivisor(int indexDivisor) throws IllegalStateException {
        if (indexDivisor < 1)
            throw new IllegalArgumentException("indexDivisor must be > 0: got " + indexDivisor);

        if (indexTerms != null)
            throw new IllegalStateException("index terms are already loaded");

        this.indexDivisor = indexDivisor;
        totalIndexInterval = origEnum.indexInterval * indexDivisor;
    }

    /** Returns the indexDivisor.
     * @see #setIndexDivisor
     */
    public int getIndexDivisor() {
        return indexDivisor;
    }

    final void close() throws IOException {
        if (origEnum != null)
            origEnum.close();
        if (indexEnum != null)
            indexEnum.close();
        enumerators.set(null);
    }

    /** Returns the number of term/value pairs in the set. */
    final long size() {
        return size;
    }

    private SegmentTermEnum getEnum() {
        SegmentTermEnum termEnum = (SegmentTermEnum)enumerators.get();
        if (termEnum == null) {
            termEnum = terms();
            enumerators.set(termEnum);
        }
        return termEnum;
    }

    private synchronized void ensureIndexIsRead() throws IOException {
        if (indexTerms != null)                                    // index already read
            return;                                                  // do nothing
        try {
            int indexSize = 1+((int)indexEnum.size-1)/indexDivisor;  // otherwise read index

            indexTerms = new Term[indexSize];
            indexInfos = new TermInfo[indexSize];
            indexPointers = new long[indexSize];

            for (int i = 0; indexEnum.next(); i++) {
                indexTerms[i] = indexEnum.term();
                indexInfos[i] = indexEnum.termInfo();
                indexPointers[i] = indexEnum.indexPointer;

                for (int j = 1; j < indexDivisor; j++)
                    if (!indexEnum.next())
                        break;
            }
        } finally {
            indexEnum.close();
            indexEnum = null;
        }
    }

    /** Returns the offset of the greatest index entry which is less than or equal to term.*/
    private final int getIndexOffset(Term term) {
        int lo = 0;					  // binary search indexTerms[]
        int hi = indexTerms.length - 1;

        while (hi >= lo) {
            int mid = (lo + hi) >> 1;
            int delta = term.compareTo(indexTerms[mid]);
            if (delta < 0)
                hi = mid - 1;
            else if (delta > 0)
                lo = mid + 1;
            else
                return mid;
        }
        return hi;
    }

    private final void seekEnum(int indexOffset) throws IOException {
        getEnum().seek(indexPointers[indexOffset],
                (indexOffset * totalIndexInterval) - 1,
                indexTerms[indexOffset], indexInfos[indexOffset]);
    }

    /**
     * 返回TermInfo或者null
     * @param term
     * @return
     * @throws IOException
     */
    TermInfo get(Term term) throws IOException {
        if (size == 0) return null;

        ensureIndexIsRead();

        // optimize sequential access: first try scanning cached enum w/o seeking
        SegmentTermEnum enumerator = getEnum();
        if (enumerator.term() != null                 // term is at or past current
                && ((enumerator.prev() != null && term.compareTo(enumerator.prev())> 0)
                || term.compareTo(enumerator.term()) >= 0)) {
            int enumOffset = (int)(enumerator.position/totalIndexInterval)+1;
            if (indexTerms.length == enumOffset	  // but before end of block
                    || term.compareTo(indexTerms[enumOffset]) < 0)
                return scanEnum(term);			  // no need to seek
        }

        // random-access: must seek
        seekEnum(getIndexOffset(term));
        return scanEnum(term);
    }

    /**
     * 在块内扫描匹配项
     * @param term
     * @return
     * @throws IOException
     */
    private final TermInfo scanEnum(Term term) throws IOException {
        SegmentTermEnum enumerator = getEnum();
        enumerator.scanTo(term);
        if (enumerator.term() != null && term.compareTo(enumerator.term()) == 0)
            return enumerator.termInfo();
        else
            return null;
    }

    /**
     * 返回集合中的第n项
     * @param position
     * @return
     * @throws IOException
     */
    final Term get(int position) throws IOException {
        if (size == 0) return null;

        SegmentTermEnum enumerator = getEnum();
        if (enumerator != null && enumerator.term() != null &&
                position >= enumerator.position &&
                position < (enumerator.position + totalIndexInterval))
            return scanEnum(position);		  // can avoid seek

        seekEnum(position/totalIndexInterval); // must seek
        return scanEnum(position);
    }

    private final Term scanEnum(int position) throws IOException {
        SegmentTermEnum enumerator = getEnum();
        while(enumerator.position < position)
            if (!enumerator.next())
                return null;

        return enumerator.term();
    }

    /**
     * 返回Term的位置
     * @param term
     * @return
     * @throws IOException
     */
    final long getPosition(Term term) throws IOException {
        if (size == 0) return -1;

        ensureIndexIsRead();
        int indexOffset = getIndexOffset(term);
        seekEnum(indexOffset);

        SegmentTermEnum enumerator = getEnum();
        while(term.compareTo(enumerator.term()) > 0 && enumerator.next()) {}

        if (term.compareTo(enumerator.term()) == 0)
            return enumerator.position;
        else
            return -1;
    }

    /** 返回所有的段的枚举 */
    public SegmentTermEnum terms() {
        return (SegmentTermEnum)origEnum.clone();
    }
    /**
     * 返回指定的期数或开始的段集合
     */
    /** Returns an enumeration of terms starting at or after the named term. */
    public SegmentTermEnum terms(Term term) throws IOException {
        get(term);
        return (SegmentTermEnum)getEnum().clone();
    }
}
