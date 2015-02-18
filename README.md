Txalaparta. by www.ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta

this is just a bunch of scripts to research the txalaparta rhythms by implementing them into a digital system.

note that this is a ongoing research project suffering constant changes atm!

there are two main directions atm :
	- txalaparta.scd : an auto txalaparta that generates both players' output
	- txala_markov_tempo.scd : an interactive txalaparta that listens to sound input and responds. the response has three different modes
		- imitation
		- 1st order markov chain with predefined fixed values
		- 2nd order markov chain that dinamically learns from the input. it takes into considerarion both the last system's answer and the human response to that