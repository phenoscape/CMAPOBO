/*
 * CMAPOBO - a tool for building OBO files from term lists
 * 
 * Created on Jan 30, 2008
 * Last updated on July 24,2008
 *
 */
package org.phenoscape.cmapobo.parse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EntryReader {
    
    static final Pattern commaPattern = Pattern.compile(",");
    
    private enum Field{
        id,
        name,
        comment,
        status,
        synonyms
    }

    private Field [] fields;
    
    protected String ontprefix;
    
    public EntryReader(String prefix){
        ontprefix = prefix;
    }
    
    /**
     * 
     * @param f
     * @param headersFirst
     * @return
     */
    public List<Entry> processCatalog(File f,boolean headersFirst) {
        final ArrayList<Entry> result = new ArrayList<Entry>();
        String raw = "";
        String [] digest = null;
        if (f != null){
            try {
                BufferedReader br = new BufferedReader(new FileReader(f));
                raw = br.readLine();
                while (raw != null){
                    digest = commaPattern.split(raw);
                    if (checkEntry(digest)){
                        Entry item = processLine(digest);
                        if (item != null)
                            result.add(item);
                    }
                    else{
                        System.err.println("Bad line: " + raw);
                    }
                    raw = br.readLine();
                }
            }
            catch (Exception e) {
                System.out.print(e);
                return result;
            }
        }
        return result; // for now
    }

    private boolean checkEntry(String[] line){
    	if (line.length == 3 || line.length == 9)
    		return true;
    	else
    		return false;
    }
    
   

    private Entry processLine(String[] digest){
    	final Entry result = new Entry(); 
    	result.sourceTerm = digest[0];
    	String relationStr = null;
    	if (digest.length == 9){
    		result.destTerm = digest[2];
    		if (digest[1].indexOf('"')>-1){
    			result.relation = digest[1].substring(1,digest[1].length()-1);
    		}
    		else
    			result.relation = digest[1];
        	// need to strip out spaces from relation names
        	while (result.relation.indexOf(' ')>-1){
        		int spacePos = result.relation.indexOf(' ');
        		if (result.relation.length()>spacePos+1 && result.relation.charAt(spacePos+1)==' ')
        			result.relation = result.relation.substring(0,spacePos) + '_' + result.relation.substring(spacePos+2);
        		else if(result.relation.charAt(spacePos-1)=='_' || result.relation.charAt(spacePos+1)=='_')
        			result.relation = result.relation.substring(0,spacePos) + result.relation.substring(spacePos+1);        			
        		else
        			result.relation = result.relation.substring(0,spacePos) + '_' + result.relation.substring(spacePos+1);
        	}
    	}
    	else{
    		result.relation = null;
    		result.destTerm = null;
    	}
    	return result;
    }
    
        
    
    
        

}
