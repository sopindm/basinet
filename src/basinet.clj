(ns basinet
  (:refer-clojure :exclude [pop]))

;;
;; Basic channel functions
;;

(defn open? [channel] (.isOpen channel))
(defn close [channel] (.close channel))

;;
;; Basic stream functions
;;

(defn- option->nullable [option] (if (not (.isEmpty option)) (.get option)))

(defn push [sink item] (.push sink item))
(defn push-in [sink item milliseconds] (.pushIn sink item milliseconds))
(defn try-push [sink item] (.tryPush sink item))

(defn pop [source] (.pop source))
(defn pop-in [source milliseconds] (option->nullable (.popIn source milliseconds)))
(defn try-pop [source] (option->nullable (.tryPop source)))

;;
;; Sockets
;; 

(defn source [socket] (.source socket))
(defn sink [socket] (.sink socket))

(defn pipe [] (.apply basinet.Pipe$/MODULE$))


  
