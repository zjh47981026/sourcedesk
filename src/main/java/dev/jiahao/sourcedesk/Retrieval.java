package dev.jiahao.sourcedesk;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class Retrieval {
    public record Hit(Documents.Chunk chunk,double score) {}
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP=Set.of("a","an","the","is","are","was","were","be","to","of","in","on","at","for","and","or","with","what","which","how","can","i","my","do","does","it","this","that","we","you","our","their","me","about","many","much","have","has");
    static List<String> tokens(String s) {
        var result=new ArrayList<String>(); var m=WORD.matcher(s.toLowerCase(Locale.ROOT));
        while(m.find()) { String t=m.group(); if(!STOP.contains(t)) result.add(t); }
        return result;
    }
    public List<Hit> search(String query,List<Documents.Chunk> chunks) {
        Set<String> terms=new LinkedHashSet<>(tokens(query));
        if(terms.isEmpty() || chunks.isEmpty()) return List.of();
        var bag=chunks.stream().map(c->tokens(c.text())).toList();
        double average=bag.stream().mapToInt(List::size).average().orElse(1);
        List<Hit> hits=new ArrayList<>();
        for(int i=0;i<chunks.size();i++) {
            List<String> words=bag.get(i); double score=0; int matches=0;
            for(String term:terms) {
                long frequency=words.stream().filter(term::equals).count();
                if(frequency==0) continue; matches++;
                long df=bag.stream().filter(w->w.contains(term)).count();
                double idf=Math.log(1+(chunks.size()-df+0.5)/(df+0.5));
                score+=idf*frequency*2.2/(frequency+1.2*(0.25+0.75*words.size()/average));
            }
            // Lexical relevance only: this is not a calibrated confidence or entailment score.
            if(matches>=Math.min(2,terms.size()) && (double)matches/terms.size()>=0.3) hits.add(new Hit(chunks.get(i),score));
        }
        return hits.stream().sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(5).toList();
    }
}
