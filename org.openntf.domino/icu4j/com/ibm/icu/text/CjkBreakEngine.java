/*
 *******************************************************************************
 * Copyright (C) 2012, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package com.ibm.icu.text;

import static com.ibm.icu.impl.CharacterIteration.DONE32;
import static com.ibm.icu.impl.CharacterIteration.current32;
import static com.ibm.icu.impl.CharacterIteration.next32;

import java.io.IOException;
import java.text.CharacterIterator;
import java.util.Stack;

import com.ibm.icu.impl.Assert;

class CjkBreakEngine implements LanguageBreakEngine {
    private static final UnicodeSet fHangulWordSet = new UnicodeSet();
    private static final UnicodeSet fHanWordSet = new UnicodeSet();
    private static final UnicodeSet fKatakanaWordSet = new UnicodeSet();
    private static final UnicodeSet fHiraganaWordSet = new UnicodeSet();
    static {
        fHangulWordSet.applyPattern("[\\uac00-\\ud7a3]");
        fHanWordSet.applyPattern("[:Han:]");
        fKatakanaWordSet.applyPattern("[[:Katakana:]\\uff9e\\uff9f]");
        fHiraganaWordSet.applyPattern("[:Hiragana:]");
        
        // freeze them all
        fHangulWordSet.freeze();
        fHanWordSet.freeze();
        fKatakanaWordSet.freeze();
        fHiraganaWordSet.freeze();
    }

    private final UnicodeSet fWordSet;
    private DictionaryMatcher fDictionary = null;
    
    public CjkBreakEngine(boolean korean) throws IOException {
        fDictionary = DictionaryData.loadDictionaryFor("Hira");
        if (korean) {
            fWordSet = fHangulWordSet;
        } else {
            fWordSet = new UnicodeSet();
            fWordSet.addAll(fHanWordSet);
            fWordSet.addAll(fKatakanaWordSet);
            fWordSet.addAll(fHiraganaWordSet);
            fWordSet.add("\\uff70\\u30fc");
        }
    }

    public boolean handles(int c, int breakType) {
        return (breakType == BreakIterator.KIND_WORD) &&
                (fWordSet.contains(c));
    }

    private static final int kMaxKatakanaLength = 8;
    private static final int kMaxKatakanaGroupLength = 20;
    private static final int maxSnlp = 255;
    private static final int kint32max = Integer.MAX_VALUE;
    private static int getKatakanaCost(int wordlength) {
        int katakanaCost[] =  new int[] { 8192, 984, 408, 240, 204, 252, 300, 372, 480 };
        return (wordlength > kMaxKatakanaLength) ? 8192 : katakanaCost[wordlength];
    }
    
    private static boolean isKatakana(int value) {
        return (value >= 0x30A1 && value <= 0x30FE && value != 0x30FB) ||
                (value >= 0xFF66 && value <= 0xFF9F);
    }
    
    public int findBreaks(CharacterIterator inText, int startPos, int endPos,
            boolean reverse, int breakType, Stack<Integer> foundBreaks) {
        if (startPos >= endPos) {
            return 0;
        }

        inText.setIndex(startPos);

        int inputLength = endPos - startPos;
        int[] charPositions = new int[inputLength + 1];
        StringBuffer s = new StringBuffer("");
        inText.setIndex(startPos);
        while (inText.getIndex() < endPos) {
            s.append(inText.current());
            inText.next();
        }
        String prenormstr = s.toString();
        boolean isNormalized = Normalizer.quickCheck(prenormstr, Normalizer.NFKC) == Normalizer.YES ||
                               Normalizer.isNormalized(prenormstr, Normalizer.NFKC, 0);
        CharacterIterator text = inText;
        int numChars = 0;
        if (isNormalized) {
            int index = 0;
            charPositions[0] = 0;
            while (index < prenormstr.length()) {
                int codepoint = prenormstr.codePointAt(index);
                index += Character.charCount(codepoint);
                numChars++;
                charPositions[numChars] = index;
            }
        } else {
            String normStr = Normalizer.normalize(prenormstr, Normalizer.NFKC);
            text = new java.text.StringCharacterIterator(normStr);
            charPositions = new int[normStr.length() + 1];
            Normalizer normalizer = new Normalizer(prenormstr, Normalizer.NFKC, 0);
            int index = 0;
            charPositions[0] = 0;
            while (index < normalizer.endIndex()) {
                normalizer.next();
                numChars++;
                index = normalizer.getIndex();
                charPositions[numChars] = index;
            }
        }
        
        // From here on out, do the algorithm. Note that our indices
        // refer to indices within the normalized string.
        int[] bestSnlp = new int[numChars + 1];
        bestSnlp[0] = 0;
        for (int i = 1; i <= numChars; i++) {
            bestSnlp[i] = kint32max;
        }

        int[] prev = new int[numChars + 1];
        for (int i = 0; i <= numChars; i++) {
            prev[i] = -1;
        }
        
        final int maxWordSize = 20;
        int values[] = new int[numChars];
        int lengths[] = new int[numChars];
        // dynamic programming to find the best segmentation
        boolean is_prev_katakana = false;
        for (int i = 0; i < numChars; i++) {
            text.setIndex(i);
            if (bestSnlp[i] == kint32max) {
                continue;
            }
            
            int maxSearchLength = (i + maxWordSize < numChars) ? maxWordSize : (numChars - i);
            int[] count_ = new int[1];
            fDictionary.matches(text, maxSearchLength, lengths, count_, maxSearchLength, values);
            int count = count_[0];
            
            // if there are no single character matches found in the dictionary 
            // starting with this character, treat character as a 1-character word
            // with the highest value possible (i.e. the least likely to occur).
            // Exclude Korean characters from this treatment, as they should be 
            // left together by default.
            if ((count == 0 || lengths[0] != 1) && current32(text) != DONE32 && !fHangulWordSet.contains(current32(text))) {
                values[count] = maxSnlp;
                lengths[count] = 1;
                count++;
            }

            for (int j = 0; j < count; j++) {
                int newSnlp = bestSnlp[i] + values[j];
                if (newSnlp < bestSnlp[lengths[j] + i]) {
                    bestSnlp[lengths[j] + i] = newSnlp;
                    prev[lengths[j] + i] = i;
                }
            }
            
            // In Japanese, single-character Katakana words are pretty rare.
            // So we apply the following heuristic to Katakana: any continuous
            // run of Katakana characters is considered a candidate word with
            // a default cost specified in the katakanaCost table according 
            // to its length.
            text.setIndex(i);
            boolean is_katakana = isKatakana(current32(text));
            if (!is_prev_katakana && is_katakana) {
                int j = i + 1;
                next32(text);
                while (j < numChars && (j - i) < kMaxKatakanaGroupLength && isKatakana(current32(text))) {
                    next32(text);
                    ++j;
                }
                
                if ((j - i) < kMaxKatakanaGroupLength) {
                    int newSnlp = bestSnlp[i] + getKatakanaCost(j - i);
                    if (newSnlp < bestSnlp[j]) {
                        bestSnlp[j] = newSnlp;
                        prev[j] = i;
                    }
                }
            }
            is_prev_katakana = is_katakana;
        }

        int t_boundary[] = new int[numChars + 1];
        int numBreaks = 0;
        if (bestSnlp[numChars] == kint32max) {
            t_boundary[numBreaks] = numChars;
            numBreaks++;
        } else {
            for (int i = numChars; i > 0; i = prev[i]) {
                t_boundary[numBreaks] = i;
                numBreaks++;
            }
            Assert.assrt(prev[t_boundary[numBreaks - 1]] == 0);
        }

        if (foundBreaks.size() == 0 || foundBreaks.peek() < startPos) {
            t_boundary[numBreaks++] = 0;
        }

        for (int i = numBreaks - 1; i >= 0; i--) {
            int pos = charPositions[t_boundary[i]] + startPos;
            if (!(foundBreaks.contains(pos) || pos == startPos))
                foundBreaks.push(charPositions[t_boundary[i]] + startPos);
        }

        if (!foundBreaks.empty() && foundBreaks.peek() == endPos)
            foundBreaks.pop();
        if (!foundBreaks.empty()) 
            inText.setIndex(foundBreaks.peek());
        return 0;
    }
}
