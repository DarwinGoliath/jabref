/**
 *
 */
package org.jabref.logic.importer.fileformat;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jabref.logic.importer.Importer;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
public class MrDLibImporter extends Importer {

    private static final MessageFormat htmlListItemTemplate = new MessageFormat("<a href=''{0}''><font color='#000000' size='4' face='Arial, Helvetica, sans-serif'>{1}</font></a>. <font color='#000000' size='4' face='Arial, Helvetica, sans-serif'>{2} <i>{3}</i>. {4}</font>");
    private static final Logger LOGGER = LoggerFactory.getLogger(MrDLibImporter.class);
    public ParserResult parserResult;

    @SuppressWarnings("unused")
    @Override
    public boolean isRecognizedFormat(BufferedReader input) throws IOException {
        LOGGER.info("Checking for recognised format.");
        String recommendationsAsString = convertToString(input);
        try {
            new JSONObject(recommendationsAsString);
        } catch (JSONException ex) {
            return false;
        }
        LOGGER.info("Valid format confirmed.");
        return true;
    }

    @Override
    public ParserResult importDatabase(BufferedReader input) throws IOException {
        parse(input);
        return parserResult;
    }

    @Override
    public String getName() {
        return "MrDLibImporter";
    }

    @Override
    public StandardFileType getFileType() {
        return StandardFileType.XML;
    }

    @Override
    public String getDescription() {
        return "Takes valid xml documents. Parses from MrDLib API a BibEntry";
    }

    /**
     * Convert Buffered Reader response to string for JSON parsing.
     * @param Takes a BufferedReader with a reference to the JSON document delivered by mdl server.
     * @return Returns an String containing the JSON document.
     * @throws IOException
     */
    private String convertToString(BufferedReader input) throws IOException {
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = input.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
        return stringBuilder.toString();
    }

    /**
     * Small pair-class to ensure the right order of the recommendations.
     */
    private class RankedBibEntry {

        public BibEntry entry;
        public Integer rank;

        public RankedBibEntry(BibEntry entry, Integer rank) {
            this.rank = rank;
            this.entry = entry;
        }
    }

    /**
     * Parses the input from the server to a ParserResult
     * @param input A BufferedReader with a reference to a string with the servers response
     * @throws IOException
     */
    private void parse(BufferedReader input) throws IOException {
        // The Bibdatabase that gets returned in the ParserResult.
        BibDatabase bibDatabase = new BibDatabase();
        // The document to parse
        String recommendations = convertToString(input);

        // The sorted BibEntries gets stored here later
        List<BibEntry> bibEntries = new ArrayList<>();

        JSONObject recommendationsJson = new JSONObject(recommendations);
        recommendationsJson = recommendationsJson.getJSONObject("recommendations");
        LOGGER.info("Recommendations: " + recommendationsJson);
        Iterator<String> keys = recommendationsJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            LOGGER.info("Starting on key: " + key);
            JSONObject value = recommendationsJson.getJSONObject(key);
            LOGGER.info("Recommendation: \n" + value);
            BibEntry currentEntry = populateBibEntry(value);
            bibEntries.add(currentEntry);
        }

        for (BibEntry bibentry : bibEntries) {
            bibDatabase.insertEntry(bibentry);
        }
        parserResult = new ParserResult(bibDatabase);
    }

    private BibEntry populateBibEntry(JSONObject recommendation) {
        BibEntry current = new BibEntry();
        LOGGER.info("Starting gethering info.");
        String authors = "", title = "", year = "", journal = "", url = "";
        if (recommendation.has("authors") && !recommendation.isNull("authors")) {
            authors = recommendation.getJSONArray("authors").toString();
        }
        LOGGER.info("Authors done");
        if (recommendation.has("title") && !recommendation.isNull("title")) {
            title = recommendation.getString("title");
        }
        LOGGER.info("Title done");
        if (recommendation.has("date_published") && !recommendation.isNull("date_published")) {
            year = recommendation.getString("date_published");
        }
        LOGGER.info("Year done");
        if (recommendation.has("published_in") && !recommendation.isNull("published_in")) {
            journal = recommendation.getString("published_in");
        }
        LOGGER.info("Journal done");
        if (recommendation.has("url") && !recommendation.isNull("url")) {
            url = recommendation.getString("url");
        }
        LOGGER.info("URL done");

        current.setField(FieldName.AUTHOR, authors);
        current.setField(FieldName.TITLE, title);
        current.setField(FieldName.YEAR, year);
        current.setField(FieldName.JOURNAL, journal);

        Object[] args = {url, title, authors, journal, year};
        String htmlRepresentation = htmlListItemTemplate.format(args);
        LOGGER.info("HTML: \n" + htmlRepresentation);
        current.setField("html_representation", htmlRepresentation);
        return current;
    }

    public ParserResult getParserResult() {
        return parserResult;
    }
}
