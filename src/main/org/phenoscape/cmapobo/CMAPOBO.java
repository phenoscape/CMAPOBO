package org.phenoscape.cmapobo;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bbop.dataadapter.DataAdapterException;
import org.obo.dataadapter.DefaultOBOParser;
import org.obo.dataadapter.OBOParseEngine;
import org.obo.dataadapter.OBOSerializationEngine;
import org.obo.dataadapter.OBO_1_2_Serializer;
import org.obo.datamodel.OBOClass;
import org.obo.datamodel.OBOProperty;
import org.obo.datamodel.OBORestriction;
import org.obo.datamodel.OBOSession;
import org.obo.datamodel.ObjectFactory;
import org.obo.datamodel.impl.DefaultObjectFactory;
import org.obo.datamodel.impl.OBOPropertyImpl;
import org.obo.util.TermUtil;
import org.phenoscape.cmapobo.parse.Entry;
import org.phenoscape.cmapobo.parse.EntryReader;


public class CMAPOBO {


	File sourceFile;
	File destFile;

	OBOSession theSession = null;
	ObjectFactory oboFactory = null;
	
	static Logger logger = Logger.getLogger(CMAPOBO.class.getName());

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		BasicConfigurator.configure();
		logger.setLevel(Level.INFO);
		if (args.length < 2){
			System.err.println("Usage: cmapobo file ontprefix");
			System.err.println("file - CMAP 'lifemap' format export");
			System.err.println("ontprefix");
			return;
		}
		final String sourceFileName = args[0];
		final String ontPrefix = args[1];
		CMAPOBO builder = new CMAPOBO();
		builder.process(sourceFileName, ontPrefix);

	}

	void process(String sourceName, String prefix){
		logger.debug("Processing with source = " + sourceName);
		sourceFile = new File(sourceName);
		EntryReader er = new EntryReader(prefix);
		List<Entry>entryList = er.processCatalog(sourceFile, true);
		final int lastdot = sourceName.lastIndexOf('.');
		String destString = sourceName.substring(0,lastdot) + ".obo";
		theSession =  DefaultObjectFactory.getFactory().createSession();
		ObjectFactory oboFactory = theSession.getObjectFactory();
		Collection<OBOClass> terms = TermUtil.getTerms(theSession);
		Map<String,OBOClass> termNames = getAllTermNamesHash(terms);
		final OBOProperty isaProperty = OBOProperty.IS_A;
		if (isaProperty == null){
			logger.fatal("is_a property not found");
			System.exit(0);
		}

		for(Entry e : entryList){
			OBOClass sourceClass;
			String termName = e.sourceTerm;
			if (termNames.get(termName) == null){
				sourceClass = (OBOClass)oboFactory.createObject(genID(prefix), OBOClass.OBO_CLASS, false);
				sourceClass.setName(termName);
				installTerm(sourceClass,termNames);
			}
			else
				sourceClass = termNames.get(termName);
			if (e.relation != null){   // is there something 
				String relationName = e.relation;
				String targetName = e.destTerm;
				OBOClass destClass;
				if (termNames.get(targetName) == null){
					destClass = (OBOClass)oboFactory.createObject(genID(prefix), OBOClass.OBO_CLASS, false);
					destClass.setName(targetName);
					installTerm(destClass,termNames);
				}
				else
					destClass = termNames.get(targetName);
				if (relationName.equals("is_a")){
                    OBORestriction res = oboFactory.createOBORestriction(sourceClass,OBOProperty.IS_A,destClass,false);
                    sourceClass.addParent(res);
                    destClass.addChild(res);
				}
				else if (relationName.equals("part_of")){
					final OBOProperty part_ofProp = lookupProperty(theSession,"OBO_REL:part_of");
                    OBORestriction res = oboFactory.createOBORestriction(sourceClass,part_ofProp,destClass,false);
                    sourceClass.addParent(res);
                    destClass.addChild(res);
				}
				else {
					final OBOProperty thisProp = lookupProperty(theSession,relationName);
                    OBORestriction res = oboFactory.createOBORestriction(sourceClass,thisProp,destClass,false);
                    sourceClass.addParent(res);
                    destClass.addChild(res);					
				}
			}
		}
		saveOBOSession(theSession,destString);
	}

	
    private OBOProperty lookupProperty(OBOSession session,String name){
        OBOProperty result = null;
        Iterator<OBOProperty> relationIter = TermUtil.getRelationshipTypes(theSession).iterator();   
        while (relationIter.hasNext() && result == null){
            OBOProperty p = (OBOProperty)relationIter.next();
            if (p.getID().equalsIgnoreCase(name))
                result = p;
        }
        if (result == null){
        	OBOProperty newProp = (OBOProperty) new OBOPropertyImpl(name,name);
        	theSession.addObject(newProp);
        	logger.debug("Creating new property: " + name);
        	result = newProp;
        }
        return result;
    }

	private int idCounter = 0;
	private String genID(String prefix){
		return prefix + ":" + Integer.toString(idCounter++);
	}

	//documentation for addObject() says not to do this unless this is a data adaptor - but effectively this is a CoF data adaptor
	//TODO: maybe restructure this class to behave like an adaptor
	private void installTerm(OBOClass newTerm,Map<String,OBOClass> termNames){
		theSession.addObject(newTerm);
		termNames.put(newTerm.getName(), newTerm);
	}


	private Map<String,OBOClass> getAllTermNamesHash(Collection <OBOClass> terms){
		final HashMap<String,OBOClass> result = new HashMap<String,OBOClass>(terms.size());
		for (OBOClass item : terms){
			if (item.getName() == null)
				System.err.println("Term " + item.getID() + " has null for name");
			else {
				if (result.get(item.getName()) != null)
					System.err.println("Hash collision in building names hash; Name = " + item.getName() + " old ID = " + ((OBOClass)result.get(item.getName())).getID() + " new ID = " + item.getID());
				else
					result.put(item.getName(), item);
			}
		}
		return result;
	}


	private OBOSession getOBOSession(String path) {
		DefaultOBOParser parser = new DefaultOBOParser();
		OBOParseEngine engine = new OBOParseEngine(parser);
		engine.setPath(path);
		return parser.getSession();
	}

	private void saveOBOSession(OBOSession session,String OBODst){
		logger.debug("About to save to " + OBODst);
		OBO_1_2_Serializer serializer = new OBO_1_2_Serializer();
		OBOSerializationEngine se = new OBOSerializationEngine();
		try {
			se.serialize(session, serializer, OBODst);
		} catch (DataAdapterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
