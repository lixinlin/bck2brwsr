package org.apidesign.bck2brwsr.htmlpage;

import org.apidesign.bck2brwsr.htmlpage.api.OnClick;
import org.apidesign.bck2brwsr.htmlpage.api.Page;

/** Trivial demo for the bck2brwsr project. First of all start
 * with <a href="TestPage.html">your XHTML page</a>. Include there
 * a script that will <em>boot Java</em> in your browser.
 * <p>
 * Then use <code>@Page</code> annotation to 
 * generate a Java representation of elements with IDs in that page.
 * Depending on the type of the elements, they will have different 
 * methods (e.g. <code>PG_TITLE</code> has <code>setText</code>, etc.).
 * Use <code>@OnClick</code> annotation to associate behavior
 * with existing elements. Use the generated elements
 * (<code>PG_TITLE</code>, <code>PG_TEXT</code>) to modify the page.
 * <p>
 * Everything is type-safe. As soon as somebody modifies the page and
 * removes the IDs or re-assigns them to wrong elements. Java compiler
 * will emit an error.
 * <p>
 * Welcome to the type-safe HTML5 world!
 *
 * @author Jaroslav Tulach <jtulach@netbeans.org>
 */
@Page(xhtml="TestPage.html", name="TestPage")
public class PageController {
    @OnClick(id="pg.button")
    static void updateTitle() {
        TestPage.PG_TITLE.setText(TestPage.PG_TEXT.getValue());
    }
}