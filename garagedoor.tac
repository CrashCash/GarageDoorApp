#!/usr/bin/python

# start with:
# twistd --pidfile /var/run/garagedoor.pid --python /usr/local/bin/garagedoor.tac --syslog

# test with:
# telnet -z ssl -z cert=~/raspberry_pi/garage_door/tls/cert-client.pem -z key=~/raspberry_pi/garage_door/tls/key-client.pem garagedoor 17000

# Twistd is probably the douchiest programming library out there. Every time I
# open up that code, I feel like I've wandered into a late-night bar on the
# Jersey Shore where everybody's drinking Jager-bombs, and nobody is wearing a
# shirt.  Twistd is a cool network library, but not cool enough to be named
# "Twisted".  It's the Python programmer's version of Ed Hardy clothing and a
# baseball cap with the tag still hanging off the side.  When I'm digging
# around in this code and my co-workers ask me what's up, the only appropriate
# response is "NOT NOW CHIEF. I'M STARTIN' THE FUCKIN' REACTOR."
# -- Ted Dziuba

import os
import sys
import time
import types
import struct
import socket
import pprint
import OpenSSL
import subprocess
import pifacedigitalio
import pifacecommon.interrupts

# don't pollute namespace with "import from"
import twisted.python
import twisted.internet.ssl
import twisted.internet.reactor
import twisted.internet.defer
import twisted.internet.protocol
import twisted.application.internet
import twisted.protocols.basic
import twisted.protocols.policies

# this is the port we listen on
port=17000

# which relay is connected to the garage door button?
RELAY_BUTTON=0

# buttons
PIN_BTN0=0
PIN_BTN1=1
PIN_BTN2=2
PIN_BTN3=3

# which pins are connected to which sensors?
PIN_CLOSED=4
PIN_OPEN=5
PIN_DOOR=6
PIN_BEAM=7

# LED numbers
LED_WAIT=7
LED_STATUS=6
LED_MOTOR=5
LED_BEAM=4

# depends on if the white or the grey wire from the photoelectric eye is connected
direction={0: 'CLEAR', 1: 'BLOCKED'}

# list of various sounds
sounds={'TINK': 'tink.wav',
        'DING': 'ding.wav',
        'WHOOP_UP': 'Window_DeIconify.wav',
        'WHOOP_DOWN': 'Window_Iconify.wav',
        'D6': 'Desktop6.wav',
        'D7': 'Desktop7.wav',
        'ERROR': 'defaultbeep.wav'}

# the twisted version of an interrupt listener class (twisted broke threads)
class TwistedInputEventListener(pifacedigitalio.InputEventListener):
    def activate(self):
        # watch for interrupts and enqueue events in a separate process
        self.detector.start()
        # call callback for queue items in a separate thread
        twisted.internet.reactor.callInThread(pifacecommon.interrupts.handle_events,
                                              self.pin_function_maps, self.event_queue,
                                              pifacecommon.interrupts._event_matches_pin_function_map,
                                              pifacecommon.interrupts.PortEventListener.TERMINATE_SIGNAL)

    def deactivate(self):
        self.event_queue.put(pifacecommon.interrupts.PortEventListener.TERMINATE_SIGNAL)
        self.detector.terminate()

def print_this(obj, level=0):
    if obj.tb:
        print vars(obj)

# log messages
def log(s):
    twisted.python.log.msg(s)

# debugging messages
def debug(s):
    log(s)

# play sound
def sound(key):
    twisted.internet.reactor.callInThread(sound_thread, key)

def sound_thread(key):
    if key not in sounds:
        log('unknown sound: '+key)
        return
    try:
        output=subprocess.check_output(['/usr/bin/play', '-q', '/usr/share/sounds/'+sounds[key]], stderr=subprocess.STDOUT).strip()
    except subprocess.CalledProcessError as error:
        output=error.output.strip()
    if len(output):
        log('sound output: '+output)

# reject RFI
def debounce(event, trigger):
    value=pifacedigital.input_pins[event.pin_num].value
    current=(1-value)*(2**event.pin_num)
    reported=event.interrupt_flag & event.interrupt_capture
    bounce=current != reported
    if bounce:
        log(trigger+' debounce failed')
        sound('ERROR')
    return bounce

# toggle relay to "push button" on motor
def press_button():
    twisted.internet.reactor.callInThread(press_button_thread)

def press_button_thread():
    global current_status

    log('press button')
    pifacedigital.leds[LED_MOTOR].turn_on()
    if os.path.exists('/var/run/garage_door_disarmed'):
        time.sleep(4)
        pifacedigital.leds[LED_MOTOR].turn_off()
        log('exiting - disarmed')
        return
    current_status=status_rollup()
    # pifacedigital.relays[RELAY_BUTTON].toggle() is too fast
    pifacedigital.relays[RELAY_BUTTON].turn_on()
    time.sleep(0.25)
    pifacedigital.relays[RELAY_BUTTON].turn_off()
    # so we can see the LED indication
    time.sleep(2)
    pifacedigital.leds[LED_MOTOR].turn_off()

###############################################################################

# read rollup door status
def status_rollup():
    data_closed=pifacedigital.input_pins[PIN_CLOSED].value
    data_open=pifacedigital.input_pins[PIN_OPEN].value
    if (not data_closed) and (not data_open):
        return 'TRANSIT'
    elif (data_closed) and (not data_open):
        return 'CLOSED'
    elif (not data_closed) and (data_open):
        return 'OPEN'
    return 'UNKNOWN'

# read back-door status
def status_door():
    if pifacedigital.input_pins[PIN_DOOR].value:
        return 'CLOSED'
    return 'OPEN'

# read beam status
def status_beam():
    return direction[pifacedigital.input_pins[PIN_BEAM].value]

# read armed status
def status_armed():
    if close_task_running:
        return 'ARMED'
    return 'DISARMED'

###############################################################################

# watch the beam breaks and then close the door
def start_close_task():
    if close_task_running:
        log('close_task already running')
        return
    sound('WHOOP_UP')
    twisted.internet.reactor.callInThread(start_close_task_thread)

def start_close_task_thread():
    global close_task_running, last_time

    log('start_close_task')
    close_task_running=True
    pifacedigital.leds[LED_WAIT].turn_on()
    if status_rollup() == 'CLOSED':
        press_button()
        # delay so we don't instantly exit because the door is closed
        time.sleep(2)

    last_time=None
    log('close function waiting to close door')
    while (close_task_running):
        # timeout since last beam-clear exceeded, and beam is currently clear
        if (last_time != None) and \
            (time.time()-last_time > 2) and \
            (status_beam() == 'CLEAR'):
            log('close_beam closing door')
            if  status_rollup() == 'OPEN':
                press_button()
            close_task_running=False
        # if door is closed, we're wasting time
        if status_rollup() ==  'CLOSED':
            log('close_beam exiting because door is closed')
            close_task_running=False
        time.sleep(0.5)
    pifacedigital.leds[LED_WAIT].turn_off()
    sound('WHOOP_DOWN')
    log('close_task done')

# stop executing close task
def stop_close_task():
    global close_task_running

    log('stop_close_task')
    close_task_running=False

# handle photoelectric eye events
def beam_event(event):
    global last_time

    if debounce(event, 'beam'):
        return
    if status_beam() == 'CLEAR':
        last_time=event.timestamp
        log('interrupt clear: '+str(event.timestamp))
        pifacedigital.leds[LED_BEAM].turn_off()
        sound('D6')
    else:
        last_time=None
        log('interrupt block: '+str(event.timestamp))
        pifacedigital.leds[LED_BEAM].turn_on()
        sound('D7')

# handle button press events for button near back door
def button0_event(event):
    log('button0_event')
    if debounce(event, 'button0'):
        return
    # if we're already waiting, abort
    if close_task_running:
        log('button aborted task')
        stop_close_task()
    else:
        if status_rollup() == 'CLOSED':
            log('button starting task')
            start_close_task()
        else:
            press_button()

# handle button press events for button near rollup door
def button1_event(event):
    log('button1_event')
    if debounce(event, 'button1'):
        return
    # if we're already waiting, abort
    if close_task_running:
        log('button aborted task')
        stop_close_task()
    else:
        log('button starting task')
        start_close_task()

class GarageDoorProtocol(twisted.protocols.basic.LineReceiver, twisted.protocols.policies.TimeoutMixin):
    def __init__(self):
        log('GarageDoorProtocol')
        self.response='GARAGEDOOR'
        self.statusTask=None
        self.MAX_LENGTH=100

    def connectionMade(self):
        # this is the remote address
        self.addr=self.transport.getPeer().host
        self.port=str(self.transport.getPeer().port)
        self.conn=self.addr+':'+self.port
        log('connection from: '+self.conn)
        self.delimiter='\n'
        self.cmd=None
        self.sendLine(self.response)
        # close later if no activity (time in seconds)
        self.setTimeout(10)

    def connectionLost(self, reason):
        log('connection lost: '+self.conn+' ')
        print_this(reason)
        if self.statusTask:
            log('stopping statusTask')
            pifacedigital.leds[LED_STATUS].turn_off()
            self.statusTask.stop()
        self.setTimeout(None)

    def lineReceived(self, line):
        # ignore blank lines
        if not line:
            return

        # parse the command
        words=line.split()
        cmd=words[0].lower()

        # dispatch the command to the appropriate do_* method
        try:
            method=getattr(self, 'do_'+cmd)
        except AttributeError, e:
            log('unknown command "'+cmd+'" from: '+self.conn)
            self.transport.loseConnection()
        else:
            try:
                log('command: '+cmd)
                self.resetTimeout()
                method()
            except Exception, e:
                log('error: '+str(e))
                self.transport.loseConnection()

    # close connection after timeout
    def timeoutConnection(self):
        log('connection timeout: '+self.conn)
        self.transport.abortConnection()

    # periodic task to report current state of doors
    def status(self):
        state_new=status_rollup()+' '+status_door()+' '+status_beam()+' '+status_armed()
        self.resetTimeout()
        # output only when status changes, or keepalive timer expires
        if (state_new != self.state_old) or (time.time()-self.time > 20):
            log('door states: '+state_new)
            self.sendLine(state_new)
            self.state_old=state_new
            self.time=time.time()

    # report current state of doors until the remote closes connection
    def do_status(self):
        pifacedigital.leds[LED_STATUS].turn_on()
        self.time=time.time()
        self.state_old=''
        self.statusTask=twisted.internet.task.LoopingCall(self.status)
        self.statusTask.start(0.1)

    # open/close the door immediately
    def do_toggle(self):
        press_button()
        self.sendLine('TOGGLE DONE')
        self.transport.loseConnection()

    # open door if it's closed
    def do_open(self):
        if status_rollup() == 'CLOSED':
            press_button()
        self.sendLine('OPEN DONE')
        self.transport.loseConnection()

    # close door if it's open
    def do_close(self):
        if status_rollup() == 'OPEN':
            press_button()
        self.sendLine('CLOSE DONE')
        self.transport.loseConnection()

    # open door if it's closed, then close it after the beam is broken
    def do_openclose(self):
        start_close_task()
        self.sendLine('OPENCLOSE DONE')
        self.transport.loseConnection()

    # arm/disarm the close-task
    def do_arm(self):
        if close_task_running:
            stop_close_task()
        else:
            start_close_task()

    # keep-alive
    def do_ping(self):
        pass

# task is not running
close_task_running=False

# initialize PiFace board
pifacedigital=pifacedigitalio.PiFaceDigital()

# load SSL client certificate
with open("/etc/garagedoor/cert-client.pem") as clientCertFile:
    clientCert=twisted.internet.ssl.Certificate.loadPEM(clientCertFile.read())

# load SSL server certificate & key
with open("/etc/garagedoor/key-server.pem") as keyFile:
    with open("/etc/garagedoor/cert-server.pem") as certFile:
        serverCert=twisted.internet.ssl.PrivateCertificate.loadPEM(keyFile.read()+certFile.read())

# start secure listener
factory=twisted.internet.protocol.Factory.forProtocol(GarageDoorProtocol)
contextFactory=serverCert.options(clientCert)

# disable broken session shit
contextFactory.getContext().set_session_cache_mode(OpenSSL.SSL.SESS_CACHE_OFF)

# kick everything off
application=twisted.application.service.Application('garagedoor')
server=twisted.application.service.IServiceCollection(application)
twisted.application.internet.SSLServer(port, factory, contextFactory).setServiceParent(server)

# set up a hardware interrupt listener
listener=TwistedInputEventListener(chip=pifacedigital)

# listen for beam events
# register(self, pin_num, direction, callback, settle_time=0.02)
listener.register(PIN_BEAM, pifacecommon.interrupts.IODIR_BOTH, beam_event)

# listen for button events
listener.register(PIN_BTN0, pifacecommon.interrupts.IODIR_ON, button0_event)
listener.register(PIN_BTN1, pifacecommon.interrupts.IODIR_ON, button1_event)

# clean up interrupt listener processes/threads at shutdown
twisted.internet.reactor.addSystemEventTrigger('before', 'shutdown', listener.deactivate)

# start listening for button events
listener.activate()
