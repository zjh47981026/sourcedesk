package dev.jiahao.sourcedesk;

import jakarta.servlet.http.HttpSession;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;

@Service
public class Documents {
    public record Chunk(String id, String documentId, String filename, int page, String text) {}
    public record Document(String id, String name, int pages, int chunkCount, Instant addedAt, List<Chunk> chunks) {}
    public record Summary(String id, String name, int pages, int chunkCount, Instant addedAt) {}
    static class Workspace {
        final LinkedHashMap<String,Document> documents = new LinkedHashMap<>();
        final Map<String,String> fingerprints = new HashMap<>();
    }
    private Workspace workspace(HttpSession session) {
        synchronized (session) {
            var current = (Workspace)session.getAttribute("workspace");
            if (current == null) { current = new Workspace(); session.setAttribute("workspace",current); }
            return current;
        }
    }
    public List<Summary> list(HttpSession session) {
        var w=workspace(session);
        synchronized(w) { return w.documents.values().stream().map(this::summary).toList(); }
    }
    Summary summary(Document d) { return new Summary(d.id(),d.name(),d.pages(),d.chunkCount(),d.addedAt()); }
    public Summary upload(HttpSession session, MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>10*1024*1024) throw new IllegalArgumentException("Choose a nonempty file up to 10 MB.");
        String name=Objects.requireNonNullElse(file.getOriginalFilename(),"document.txt").replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "");
        if(name.length()>120) name=name.substring(name.length()-120);
        byte[] data=file.getBytes();
        List<String> pages=new ArrayList<>();
        if(name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            if(data.length<5 || !new String(data,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) throw new IllegalArgumentException("This file is not a valid PDF.");
            try(var pdf=Loader.loadPDF(data)) {
                if(pdf.isEncrypted()) throw new IllegalArgumentException("Password-protected PDFs are not supported.");
                if(pdf.getNumberOfPages()>100) throw new IllegalArgumentException("Please use a PDF with at most 100 pages.");
                var stripper=new PDFTextStripper();
                int chars=0;
                for(int p=1;p<=pdf.getNumberOfPages();p++) {
                    stripper.setStartPage(p); stripper.setEndPage(p);
                    String text=stripper.getText(pdf); chars+=text.length();
                    if(chars>250_000) throw new IllegalArgumentException("Document text exceeds 250,000 characters.");
                    pages.add(text);
                }
            } catch(IllegalArgumentException e) { throw e; }
            catch(IOException e) { throw new IllegalArgumentException("PDF could not be read. Try an unencrypted, text-based PDF."); }
        } else if(name.toLowerCase(Locale.ROOT).matches(".*\\.(txt|md)$")) {
            try { pages.add(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()); }
            catch(CharacterCodingException e) { throw new IllegalArgumentException("Text files must use UTF-8 encoding."); }
            if(pages.get(0).contains("\u0000")) throw new IllegalArgumentException("This appears to be a binary file.");
        } else throw new IllegalArgumentException("Supported files: PDF, TXT and Markdown.");
        return add(session,name,pages);
    }
    public Summary add(HttpSession session,String name,List<String> pages) {
        if(pages.stream().mapToInt(String::length).sum()>250_000) throw new IllegalArgumentException("Document text exceeds 250,000 characters.");
        String id=UUID.randomUUID().toString();
        List<Chunk> chunks=chunk(id,name,pages);
        if(chunks.isEmpty()) throw new IllegalArgumentException("No readable text found. Scanned PDFs need OCR before uploading.");
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\f",pages).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var w=workspace(session);
        synchronized(w) {
            if(w.fingerprints.containsKey(fingerprint)) return summary(w.documents.get(w.fingerprints.get(fingerprint)));
            if(w.documents.size()>=20) throw new IllegalArgumentException("This workspace holds up to 20 documents. Remove one to add another.");
            var doc=new Document(id,name,pages.size(),chunks.size(),Instant.now(),chunks);
            w.documents.put(id,doc); w.fingerprints.put(fingerprint,id); return summary(doc);
        }
    }
    static List<Chunk> chunk(String id,String name,List<String> pages) {
        List<Chunk> result=new ArrayList<>();
        for(int p=0;p<pages.size();p++) {
            String text=pages.get(p).replaceAll("\\s+"," ").trim();
            if(text.isBlank()) continue;
            String[] words=text.split(" ");
            for(int start=0;start<words.length;start+=105) {
                int end=Math.min(start+130,words.length);
                result.add(new Chunk(id+":"+p+":"+start,id,name,p+1,String.join(" ",Arrays.copyOfRange(words,start,end))));
                if(end==words.length) break;
            }
        }
        return List.copyOf(result);
    }
    public List<Chunk> selected(HttpSession session,List<String> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>20) throw new IllegalArgumentException("Select between 1 and 20 documents.");
        var w=workspace(session);
        synchronized(w) {
            List<Chunk> result=new ArrayList<>();
            for(String id:new LinkedHashSet<>(ids)) {
                var d=w.documents.get(id);
                if(d==null) throw new IllegalArgumentException("A selected document is unavailable. Refresh your document list.");
                result.addAll(d.chunks());
            }
            return List.copyOf(result);
        }
    }
    public void delete(HttpSession session,String id) {
        var w=workspace(session);
        synchronized(w) { w.documents.remove(id); w.fingerprints.values().removeIf(id::equals); }
    }
    public void clear(HttpSession session) {
        var w=workspace(session); synchronized(w) { w.documents.clear(); w.fingerprints.clear(); }
    }
    public void demo(HttpSession session) {
        add(session,"Northstar employee handbook.txt",List.of("""
            Northstar Labs Employee Handbook — Fictional sample, not a real company policy.
            Annual leave: Full-time employees receive 20 days of paid annual leave each calendar year. Unused annual leave may carry over up to five days into the following year. Manager approval is required before booking annual leave. The leave request must be submitted through the People Portal at least 10 business days before the planned start date.
            Remote work: Employees may work remotely up to three days per week. New employees must work from the office during their first two weeks. International remote work requires written approval from the People team and is limited to 15 days annually.
            Learning budget: Employees receive an annual professional development budget of $1,200. Courses, books and professional conferences are eligible. Purchases above $300 require manager approval. Receipts must be submitted within 30 days of purchase. The budget resets on January 1 and does not carry forward.
            Expenses: Business expenses must be submitted within 30 days with itemized receipts. Reimbursements are processed on the next monthly payroll after approval.
            ""","""
            Northstar Labs Employee Handbook — Page 2.
            Equipment and security: Employees must enable multi-factor authentication for work accounts and use company-managed devices. Report lost devices to the security team within one hour. Do not upload customer information to unapproved public AI services.
            Parental leave: Eligible employees receive 12 weeks of paid parental leave after six months of employment. Notify the People team at least 30 days before the anticipated leave date when possible.
            """));
        add(session,"Northstar travel policy.txt",List.of("""
            Northstar Labs Business Travel Policy — Fictional sample.
            Air travel: Book economy class for domestic flights. International flights longer than eight hours may be booked in premium economy with manager approval. All travel must be approved before booking.
            Lodging: The hotel allowance is $180 per night before tax. In New York and San Francisco, the hotel allowance is $250 per night before tax. Submit the hotel invoice with your reimbursement request.
            Meals: The daily meal allowance is $65. Alcohol and personal entertainment are not reimbursable. Itemized meal receipts are required for reimbursement. Meals already provided by a conference cannot also be claimed.
            Ground transport: Public transport and rideshare services are reimbursable for business travel. Rental cars require prior approval. Receipts must be submitted within 30 days of the trip ending.
            """));
    }
}
