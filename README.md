<img src="https://github.com/RHEAGROUP/CDP4-SDK-Community-Edition/raw/master/CDP-Community-Edition.png" width="250">

The Concurrent Design Platform Software Development Kit is an SDK that is compliant with ECSS-E-TM-10-25A Annex A and Annex C. 

This repository contains an example project that demonstrates how this Java SDK can be used.
The source code of the SDK can be found [here](https://github.com/RHEAGROUP/CDP4-SDKJ-Community-Edition).
This is a simple console application. The most convenient way to run it is through your favourite IDE.
There is a short list of predefined commands:

- **open** - Open a connection to a data-source
- **refresh** - Update the Cache with updated Things from a data-source
- **reload** - Reload all Things from a data-source for all open TopContainers
- **close** - Close the connection to a data-source and clear the Cache and exits the program
- **restore** - Restores the state of a data-source to its default state
- **get_iteration** - gets a predefined iteration of an engineering model with dependent objects
- **post_person** - posts a predefined person with 2 e-mail addresses
- **post_parameter** - posts a predefined parameter
- **post_pfsl** - posts a predefined PossibleFiniteStateList with 2 PossibleFiniteStates
- **post_pfsl_reorder** - reorders(rotates in this particular case) states in the created predefined PossibleFiniteStateList (post_pfsl)
- **remove_parameter** - removes a predefined Parameter of ElementDefinition

You will be prompted with this list after each operation for the convenience. Commands are case sensitive and
there is no autocompletion functionality.  