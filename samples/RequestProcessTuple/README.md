## Example using HTTPRequestResponse, tuple version.

The application posts a form to the browser, prompting for input. Form submission 
passes parameters to the HTTPRequestResponse operator and on into the Streams application. 

Streams builds a response and sends it back out the originating HTTPRequestResponse
operator. 

The response utilizes the [handlebars](http://handlebarsjs.com/) to render the html that is displayed in a browser.

When the Streams application is running application is accessed via 
http://localhost:8080
