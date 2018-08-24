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
import java.util.stream.Collectors;

import org.jabref.logic.importer.Importer;
import org.jabref.logic.importer.ParserResult;
import org.jabref.logic.util.StandardFileType;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 */
public class MrDLibImporter extends Importer {

    private static final MessageFormat HTML_LIST_ITEM_TEMPLATE = new MessageFormat("<a href=''{0}''><font color='#000000' size='4' face='Arial, Helvetica, sans-serif'>{1}</font></a>. <font color='#000000' size='4' face='Arial, Helvetica, sans-serif'>{2} <i>{3}</i> {4}</font>");
    private static final String DEFAULT_MRDLIB_ERROR_MESSAGE = "Error while fetching from Mr.DLib.";
    private static final Logger LOGGER = LoggerFactory.getLogger(MrDLibImporter.class);
    public ParserResult parserResult;

    @SuppressWarnings("unused")
    @Override
    public boolean isRecognizedFormat(BufferedReader input) throws IOException {
        String recommendationsAsString = convertToString(input);
        try {
            JSONObject jsonObject = new JSONObject(recommendationsAsString);
            if (!jsonObject.has("recommendations")) {
                return false;
            }
        } catch (JSONException ex) {
            return false;
        }
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
        return StandardFileType.JSON;
    }

    @Override
    public String getDescription() {
        return "Takes valid JSON documents from the Mr. DLib API and parses them into a BibEntry";
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
     * @param input A BufferedReader with a reference to a string with the server's response
     * @throws IOException
     */
    private void parse(BufferedReader input) throws IOException {
        // The Bibdatabase that gets returned in the ParserResult.
        BibDatabase bibDatabase = new BibDatabase();
        // The document to parse
        String recommendations = convertToString(input);
        // The sorted BibEntries gets stored here later
        List<RankedBibEntry> rankedBibEntries = new ArrayList<>();

        // Get recommendations from response and populate bib entries
        JSONObject recommendationsJson = new JSONObject(recommendations);
        recommendationsJson = recommendationsJson.getJSONObject("recommendations");
        Iterator<String> keys = recommendationsJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject value = recommendationsJson.getJSONObject(key);
            rankedBibEntries.add(populateBibEntry(value));
        }

        // Sort bib entries according to rank
        rankedBibEntries.sort((RankedBibEntry rankedBibEntry1,
                RankedBibEntry rankedBibEntry2) -> rankedBibEntry1.rank.compareTo(rankedBibEntry2.rank));
        List<BibEntry> bibEntries = rankedBibEntries.stream().map(e -> e.entry).collect(Collectors.toList());

        for (BibEntry bibentry : bibEntries) {
            bibDatabase.insertEntry(bibentry);
        }
        parserResult = new ParserResult(bibDatabase);
    }

    /**
     * Parses the JSON recommendations into bib entries
     * @param recommendation JSON object of a single recommendation returned by Mr. DLib
     * @return A ranked bib entry created from the recommendation input
     */
    private RankedBibEntry populateBibEntry(JSONObject recommendation) {
        BibEntry current = new BibEntry();
        String authors = "", title = "", year = "", journal = "", url = "";
        Integer rank = 100;

        // parse each of the relevant fields into variables
        if (recommendation.has("authors") && !recommendation.isNull("authors")) {
            authors = getAuthorsString(recommendation);
        }
        if (recommendation.has("title") && !recommendation.isNull("title")) {
            title = recommendation.getString("title");
        }
        if (recommendation.has("date_published") && !recommendation.isNull("date_published")) {
            year = recommendation.getString("date_published");
        }
        if (recommendation.has("published_in") && !recommendation.isNull("published_in")) {
            journal = recommendation.getString("published_in");
        }
        if (recommendation.has("url") && !recommendation.isNull("url")) {
            url = recommendation.getString("url") + ".";
        }
        if (recommendation.has("recommendation_id") && !recommendation.isNull("recommendation_id")) {
            rank = recommendation.getInt("recommendation_id");
        }

        // Populate bib entry with relevant data
        current.setField(FieldName.AUTHOR, authors);
        current.setField(FieldName.TITLE, title);
        current.setField(FieldName.YEAR, year);
        current.setField(FieldName.JOURNAL, journal);

        // Create HTML representation of recommendation for display on the UI
        Object[] args = {url, title, authors, journal, year};
        String htmlRepresentation = HTML_LIST_ITEM_TEMPLATE.format(args);
        current.setField("html_representation", htmlRepresentation);

        return new RankedBibEntry(current, rank);
    }

    /**
     * Creates an authors string from a JSON recommendation
     * @param recommendation JSON Object recommendation from Mr. DLib
     * @return A string of all authors, separated by commas and finished with a full stop.
     */
    private String getAuthorsString(JSONObject recommendation) {
        String authorsString = "";
        JSONArray array = recommendation.getJSONArray("authors");
        for (int i = 0; i < array.length(); ++i) {
            authorsString += array.getString(i) + ", ";
        }
        int stringLength = authorsString.length();
        if (stringLength > 2) {
            authorsString = authorsString.substring(0, stringLength - 2) + ".";
        }
        return authorsString;
    }

    public ParserResult getParserResult() {
        return parserResult;
    }

    /**
     * Gets the error message to be returned if there has been an error in returning recommendations.
     * Returns default error message if there is no message from Mr. DLib.
     * @param response The response from the MDL server as a string.
     * @return String error message to be shown to the user.
     */
    public String getResponseErrorMessage(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            if (!jsonObject.has("message")) {
                return jsonObject.getString("message");
            }
        } catch (JSONException ex) {
            return DEFAULT_MRDLIB_ERROR_MESSAGE;
        }
        return DEFAULT_MRDLIB_ERROR_MESSAGE;
    }
}
