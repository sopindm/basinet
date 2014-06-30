(ns basinet
  (:require [basinet.scala :as scala])
  (:refer-clojure :exclude [pop]))

;;
;; Basic channel functions
;;

(defn open? [channel] (.isOpen channel))
(defn close [channel] (.close channel))

;;
;; Basic stream functions
;;

(defn push [sink item] (.push sink item))
(defn push-in [sink item milliseconds] (.pushIn sink item milliseconds))
(defn try-push [sink item] (.tryPush sink item))

(defn pop [source] (.pop source))
(defn pop-in [source milliseconds] (scala/option->nullable (.popIn source milliseconds)))
(defn try-pop [source] (scala/option->nullable (.tryPop source)))

;;
;; Sockets
;; 

(defn source [socket] (.source socket))
(defn sink [socket] (.sink socket))

(defn pipe [] (.apply basinet.Pipe$/MODULE$))


  
