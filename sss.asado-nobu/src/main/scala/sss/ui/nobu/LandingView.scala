package sss.ui.nobu

import com.vaadin.navigator.View
import sss.ui.milford.design.Landing

class LandingView extends Landing with View
{

  this.signIn.addClickListener( _ => {
    getUI.getNavigator.navigateTo("wallView")
  })

}
