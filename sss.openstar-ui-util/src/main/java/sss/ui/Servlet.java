package sss.ui;

import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinServlet;

import sss.ui.reactor.ReactorActorSystem$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.util.function.UnaryOperator;

/**
 * Created by alan on 6/10/16.
 */
@SuppressWarnings("serial")
@WebServlet(value = "/*", asyncSupported = true)
public class Servlet extends VaadinServlet {

    private final UIProvider uiProvider;
    private final UnaryOperator<String> broadcastSessionEnd;

    public Servlet(UIProvider uiProvider) {
        this(uiProvider, null);
    }

    public Servlet(UIProvider uiProvider, UnaryOperator<String> broadcastSessionEnd) {
        this.uiProvider = uiProvider;
        this.broadcastSessionEnd = broadcastSessionEnd;
    }

    @Override
    public void destroy() {
        ReactorActorSystem$.MODULE$.blockingTerminate();
        log("Terminated actor system!");
        super.destroy();
    }

    @Override
    protected void servletInitialized() throws ServletException {

        if(broadcastSessionEnd != null) {
            getService().addSessionDestroyListener(event -> {
                if (event.getSession() != null) {
                    String attr = event.getSession().getSession().getId();
                    if (attr != null) {
                        broadcastSessionEnd.apply(attr);
                    }
                }

            });
        }

        getService().addSessionInitListener( event ->
                event.getSession().addUIProvider(uiProvider)
        );
    }
}
