/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i3.stat;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author paulo
 */
public class ReadingSpeed implements Serializable {

    private final static Map<String, Float> charatersInWord = new HashMap<>();
    private int [] timeDeltaPerWord = new int[1000];
    private int median;

    static{
    }



}
