package dev.jiahao.sourcedesk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceDeskTest {
    Documents docs = new Documents();
    MockHttpSession session = new MockHttpSession();
    ObjectMapper mapper = new ObjectMapper();
    Documents.Summary add(String text) { return docs.add(session,"policy.txt",List.of(text)); }
    List<Retrieval.Hit> hits() {
        var id=add("Employees receive 20 annual leave days.").id();
        return new Retrieval().search("annual leave",docs.selected(session,List.of(id)));
    }
    @Test void isolatesSessions() {
        var id=add("Private annual leave policy").id();
        assertThrows(IllegalArgumentException.class,()->docs.selected(new MockHttpSession(),List.of(id)));
    }
    @Test void deduplicatesAndAllowsReuploadAfterDelete() {
        var a=add("Some unique content"); var b=add("Some unique content");
        assertEquals(a.id(),b.id()); assertEquals(1,docs.list(session).size());
        docs.delete(session,a.id()); assertNotEquals(a.id(),add("Some unique content").id());
    }
    @Test void removedSourcesCannotBeQueried() {
        var a=add("Annual leave policy"); docs.clear(session);
        assertThrows(IllegalArgumentException.class,()->docs.selected(session,List.of(a.id())));
    }
    @Test void rejectsInvalidFiles() {
        for(var file:List.of(new MockMultipartFile("file","x.exe","text/plain",new byte[]{1}),
          new MockMultipartFile("file","x.pdf","application/pdf","Not a PDF".getBytes()),
          new MockMultipartFile("file","x.txt","text/plain",new byte[]{(byte)0xff}),
          new MockMultipartFile("file","x.txt","text/plain",new byte[]{0}),
          new MockMultipartFile("file","x.txt","text/plain",new byte[0])))
            assertThrows(IllegalArgumentException.class,()->docs.upload(session,file));
    }
    @Test void enforcesWorkspaceAndTextLimits() {
        for(int i=0;i<20;i++) add("Document number "+i);
        assertThrows(IllegalArgumentException.class,()->add("another document"));
        assertThrows(IllegalArgumentException.class,()->docs.add(new MockHttpSession(),"large",List.of("a".repeat(250001))));
    }
    @Test void extractsActualPdfPages() throws Exception {
        var out=new ByteArrayOutputStream();
        try(var pdf=new PDDocument()) {
            for(String line:List.of("Annual leave is twenty days.","Learning budget is twelve hundred dollars.")) {
                var page=new PDPage(); pdf.addPage(page);
                try(var content=new PDPageContentStream(pdf,page)) {
                    content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(50,700); content.showText(line); content.endText();
                }
            } pdf.save(out);
        }
        var d=docs.upload(session,new MockMultipartFile("file","policy.pdf","application/pdf",out.toByteArray()));
        var chunks=docs.selected(session,List.of(d.id()));
        assertEquals(2,d.pages()); assertEquals(2,chunks.get(1).page());
        assertTrue(chunks.get(1).text().contains("Learning budget"));
    }
    @Test void rejectsImageOnlyPdf() throws Exception {
        var out=new ByteArrayOutputStream();
        try(var pdf=new PDDocument()) { pdf.addPage(new PDPage()); pdf.save(out); }
        assertThrows(IllegalArgumentException.class,()->docs.upload(session,new MockMultipartFile("file","scan.pdf","application/pdf",out.toByteArray())));
    }
    @Test void retrievalRespectsSelectionAndAbstainsOnUnrelatedQuestion() {
        var first=add("Annual leave is twenty days."); add("Learning budget is twelve hundred dollars.");
        var chunks=docs.selected(session,List.of(first.id())); var r=new Retrieval();
        assertEquals(1,r.search("annual leave",chunks).size());
        assertTrue(r.search("learning budget",chunks).isEmpty());
        assertTrue(r.search("what is the",chunks).isEmpty());
    }
    @Test void sourceSearchIsExplicitWhenModelNotConfigured() {
        var result=new Answers(mapper,"http://127.0.0.1:11434","").answer("annual leave",hits());
        assertEquals("source_search",result.mode()); assertTrue(result.notice().contains("not an AI-generated"));
    }
    @Test void noEvidenceDoesNotCallModel() {
        assertEquals("no_evidence",new Answers(mapper,"http://127.0.0.1:1","test").answer("anything",List.of()).mode());
    }
    @Test void rejectsRemoteModelEndpoints() {
        assertThrows(IllegalArgumentException.class,()->new Answers(mapper,"https://example.com","test"));
    }
    Answers.Result fakeAnswer(String answer,List<String> citations) throws Exception { return fakeAnswer(answer,citations,hits()); }
    Answers.Result fakeAnswer(String answer,List<String> citations,List<Retrieval.Hit> sourceHits) throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange->{
            var request=mapper.readTree(exchange.getRequestBody());
            assertEquals("test",request.path("model").asText());
            String inner=mapper.writeValueAsString(Map.of("answer",answer,"citations",citations));
            byte[] body=mapper.writeValueAsBytes(Map.of("message",Map.of("content",inner)));
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try { return new Answers(mapper,"http://127.0.0.1:"+server.getAddress().getPort(),"test").answer("annual leave",sourceHits); }
        finally { server.stop(0); }
    }
    @Test void acceptsRealCitationLabelsFromModel() throws Exception {
        var result=fakeAnswer("Employees receive 20 days [S1].",List.of("S1"));
        assertEquals("ai",result.mode()); assertEquals(1,result.citations().size());
        assertEquals("policy.txt",result.citations().get(0).filename());
    }
    @Test void fabricatedCitationFallsBack() throws Exception {
        assertEquals("source_search",fakeAnswer("Unlimited leave [S99].",List.of("S99")).mode());
    }
    @Test void missingInlineCitationFallsBack() throws Exception {
        assertEquals("source_search",fakeAnswer("Twenty days.",List.of("S1")).mode());
    }
    @Test void unlistedInlineCitationFallsBack() throws Exception {
        assertEquals("source_search",fakeAnswer("Twenty days [S2].",List.of("S1")).mode());
    }
    @Test void unsupportedUncitedAnswerIsReplacedWithAbstention() throws Exception {
        var result=fakeAnswer("You have unlimited leave.",List.of());
        assertEquals("no_evidence",result.mode()); assertFalse(result.answer().contains("unlimited"));
    }

    @Test void acceptsCombinedReferencesToRetrievedSources() throws Exception {
        var sourceHits=List.of(new Retrieval.Hit(new Documents.Chunk("a1","a","first.txt",1,"Annual leave is twenty days."),1),new Retrieval.Hit(new Documents.Chunk("b1","b","second.txt",1,"Approval is required."),1));
        assertEquals("ai",fakeAnswer("Twenty days with approval [S1, S2].",List.of("S1","S2"),sourceHits).mode());
    }
    @Test void unknownReferenceInsideCombinedGroupIsRejected() throws Exception {
        assertEquals("source_search",fakeAnswer("Twenty days [S1, S99].",List.of("S1")).mode());
    }
    @Test void abstentionDiscardsAllModelTextBeforeInlineValidation() throws Exception {
        var r=fakeAnswer("No contractor policy [S1].",List.of());
        assertEquals("no_evidence",r.mode());assertFalse(r.answer().contains("[S1]"));
    }
}
