/* LanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules.ca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedToken;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.rules.*;
import org.languagetool.synthesis.ca.CatalanSynthesizer;
import org.languagetool.tagging.ca.CatalanTagger;
import org.languagetool.tools.StringTools;

/**
 * A rule that matches incorrect verbs (including all inflected forms) and
 * suggests correct ones instead.
 * 
 * Loads the relevant words from <code>rules/ca/replace_verbs.txt</code>.
 * 
 * @author Jaume Ortolà
 */
public class SimpleReplaceVerbsRule extends AbstractSimpleReplaceRule {
  
  private static final Map<String, List<String>> wrongWords = load("/ca/replace_verbs.txt");
  private static final Locale CA_LOCALE = new Locale("CA");

  @Override
  protected Map<String, List<String>> getWrongWords() {
    return wrongWords;
  }

  private final static String endings = "a|à|ada|ades|am|ant|ar|ara|arà|aran|aràs|aré|arem|àrem|"
      + "aren|ares|areu|àreu|aria|aríem|arien|aries|aríeu|às|àssem|assen|asses|àsseu|àssim|assin|"
      + "assis|àssiu|at|ats|au|ava|àvem|aven|aves|àveu|e|em|en|es|és|éssem|essen|esses|ésseu|éssim|"
      + "essin|essis|éssiu|eu|i|í|in|is|o|ïs";
  private static final Pattern desinencies_1conj_0 = Pattern.compile("(.+?)(" + endings + ")");
  private static final Pattern desinencies_1conj_1 = Pattern.compile("(.+)("  + endings + ")");
  private static final CatalanTagger tagger = new CatalanTagger();
  private static final CatalanSynthesizer synth = new CatalanSynthesizer();
  
  public SimpleReplaceVerbsRule(final ResourceBundle messages) throws IOException {
    super(messages);
    super.setCategory(Categories.TYPOS.getCategory(messages));
    super.setLocQualityIssueType(ITSIssueType.Misspelling);
    super.setIgnoreTaggedWords();
  }

  @Override
  public final String getId() {
    return "CA_SIMPLE_REPLACE_VERBS";
  }

  @Override
  public String getDescription() {
    return "Detecta verbs incorrectes i proposa suggeriments de canvi";
  }

  public String getShort() {
    return "Verb incorrecte";
  }

  public String getMessage(String tokenStr, List<String> replacements) {
    return "Verb incorrecte.";
  }

  public Locale getLocale() {
    return CA_LOCALE;
  }

  @Override
  public final RuleMatch[] match(final AnalyzedSentence sentence) {
    List<RuleMatch> ruleMatches = new ArrayList<>();
    AnalyzedTokenReadings[] tokens = sentence.getTokensWithoutWhitespace();

    for (AnalyzedTokenReadings tokenReadings : tokens) {
      String originalTokenStr = tokenReadings.getToken();
      if (ignoreTaggedWords && tokenReadings.isTagged()) {
        continue;
      }
      String tokenString = originalTokenStr.toLowerCase(getLocale());
      AnalyzedTokenReadings analyzedTokenReadings = null;
      String infinitive = null;
      int i = 0;
      while (i < 2 && analyzedTokenReadings == null) {
        Matcher m;
        if (i == 0) {
          m = desinencies_1conj_0.matcher(tokenString);
        } else {
          m = desinencies_1conj_1.matcher(tokenString);
        }
        if (m.matches()) {
          String lexeme = m.group(1);
          String desinence = m.group(2);
          if (desinence.startsWith("e") || desinence.startsWith("é")
              || desinence.startsWith("i") || desinence.startsWith("ï")) {
            if (lexeme.endsWith("c")) {
              lexeme = lexeme.substring(0, lexeme.length() - 1).concat("ç");
            } else if (lexeme.endsWith("qu")) {
              lexeme = lexeme.substring(0, lexeme.length() - 2).concat("c");
            } else if (lexeme.endsWith("g")) {
              lexeme = lexeme.substring(0, lexeme.length() - 1).concat("j");
            } else if (lexeme.endsWith("gü")) {
              lexeme = lexeme.substring(0, lexeme.length() - 2).concat("gu");
            } else if (lexeme.endsWith("gu")) {
              lexeme = lexeme.substring(0, lexeme.length() - 2).concat("g");
            }
          }
          if (desinence.startsWith("ï")) {
            desinence = "i" + desinence.substring(1, desinence.length());
          }
          infinitive = lexeme.concat("ar");
          if (wrongWords.containsKey(infinitive)) {
            List<String> wordAsArray = Arrays.asList("cant".concat(desinence));
            List<AnalyzedTokenReadings> analyzedTokenReadingsList = null;
            try {
              analyzedTokenReadingsList = tagger.tag(wordAsArray);
            } catch (IOException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
            if (analyzedTokenReadingsList != null) {
              analyzedTokenReadings = analyzedTokenReadingsList.get(0);
            }
          }
        }
        i++;
      }

      // synthesize replacements
      if (analyzedTokenReadings != null) {
        List<String> possibleReplacements = new ArrayList<>();
        String[] synthesized = null;
        List<String> replacementInfinitives = wrongWords.get(infinitive);
        for (String replacementInfinitive : replacementInfinitives) {
          if (replacementInfinitive.startsWith("(")) {
            possibleReplacements.add(replacementInfinitive);
          } else {
            String[] parts = replacementInfinitive.split(" "); // the first part
                                                               // is the verb
            AnalyzedToken infinitiveAsAnTkn = new AnalyzedToken(parts[0],
                "V.*", parts[0]);
            for (AnalyzedToken analyzedToken : analyzedTokenReadings) {
              try {
                synthesized = synth.synthesize(infinitiveAsAnTkn,
                    analyzedToken.getPOSTag());
              } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
              for (String s : synthesized) {
                for (int j = 1; j < parts.length; j++) {
                  s = s.concat(" ").concat(parts[j]);
                }
                if (!possibleReplacements.contains(s)) {
                  possibleReplacements.add(s);
                }
              }
            }
          }
        }
        if (possibleReplacements.size() > 0) {
          RuleMatch potentialRuleMatch = createRuleMatch(tokenReadings,
              possibleReplacements);
          ruleMatches.add(potentialRuleMatch);
        }
      }
    }
    return toRuleMatchArray(ruleMatches);
  }

  private RuleMatch createRuleMatch(AnalyzedTokenReadings tokenReadings,
      List<String> replacements) {
    String tokenString = tokenReadings.getToken();
    int pos = tokenReadings.getStartPos();

    RuleMatch potentialRuleMatch = new RuleMatch(this, pos, pos
        + tokenString.length(), getMessage(tokenString, replacements),
        getShort());

    if (StringTools.startsWithUppercase(tokenString)) {
      for (int i = 0; i < replacements.size(); i++) {
        replacements
            .set(i, StringTools.uppercaseFirstChar(replacements.get(i)));
      }
    }

    potentialRuleMatch.setSuggestedReplacements(replacements);

    return potentialRuleMatch;
  }

}
