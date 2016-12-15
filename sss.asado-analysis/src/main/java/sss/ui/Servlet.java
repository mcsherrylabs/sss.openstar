package sss.ui;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinServlet;
import sss.ui.MainUI;

import sss.ui.reactor.ReactorActorSystem$;

import javax.servlet.annotation.WebServlet;

/**
 * Created by alan on 6/10/16.
 */
@SuppressWarnings("serial")
@WebServlet(value = "/*", asyncSupported = true)
@VaadinServletConfiguration(productionMode = false, ui = MainUI.class)
public class Servlet extends VaadinServlet {

    @Override
    public void destroy() {
        ReactorActorSystem$.MODULE$.blockingTerminate();
        log("Terminated actor system!");
        super.destroy();
    }
}
