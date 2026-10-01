package org.empresajr.chatjr;

import org.empresajr.chatjr.domain.HtmlSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HtmlSanitizerTest {

    @Test
    void keepsAllowedTagsAndStripsTheirAttributes() {
        assertEquals("<p>Oi</p>", HtmlSanitizer.sanitize("<p onclick=\"x()\" style=\"color:red\">Oi</p>"));
        assertEquals("<table><tr><td>1</td></tr></table>", HtmlSanitizer.sanitize("<table><tr><td>1</td></tr></table>"));
        assertEquals("<ul><li>a</li></ul>", HtmlSanitizer.sanitize("<UL><LI>a</LI></UL>"));
    }

    @Test
    void dropsScriptAndStyleWithTheirContent() {
        assertEquals("ok", HtmlSanitizer.sanitize("<script>alert(1)</script>ok"));
        assertEquals("ok", HtmlSanitizer.sanitize("<SCRIPT SRC=//x>alert(1)</SCRIPT >ok"));
        assertEquals("x", HtmlSanitizer.sanitize("<style>p{color:red}</style>x"));
        assertEquals("y", HtmlSanitizer.sanitize("<iframe src=x>texto interno</iframe>y"));
        assertEquals("", HtmlSanitizer.sanitize("<script>alert(1)"));
    }

    @Test
    void dropsUnknownTagsButKeepsTheirText() {
        assertEquals("", HtmlSanitizer.sanitize("<img src=x onerror=alert(1)>"));
        assertEquals("clique", HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">clique</a>"));
        assertEquals("texto", HtmlSanitizer.sanitize("<div><span>texto</span></div>"));
    }

    @Test
    void brokenTagsCannotSmuggleAScript() {
        String out = HtmlSanitizer.sanitize("<scr<script>ipt>alert(1)</scr</script>ipt>");
        assertFalse(out.toLowerCase().contains("<script"), out);
        assertFalse(HtmlSanitizer.sanitize("<<script>script>alert(1)").toLowerCase().contains("<script"));
    }

    @Test
    void escapesLooseSpecialCharactersAndKeepsRealEntities() {
        assertEquals("<b>negrito</b> &amp; 5 &lt; 6 &gt; 4", HtmlSanitizer.sanitize("<b>negrito</b> & 5 < 6 > 4"));
        assertEquals("&amp; &copy; &#169; &amp;bad", HtmlSanitizer.sanitize("&amp; &copy; &#169; &bad"));
    }

    @Test
    void removesCommentsAndNormalizesLineBreaks() {
        assertEquals("<p>x</p>", HtmlSanitizer.sanitize("<!-- segredo --><p>x</p>"));
        assertEquals("a<br>b", HtmlSanitizer.sanitize("a<br/>b"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals("", HtmlSanitizer.sanitize(null));
        assertEquals("", HtmlSanitizer.sanitize(""));
    }
}
