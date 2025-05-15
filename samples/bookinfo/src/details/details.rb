#!/usr/bin/ruby
#
# Copyright Istio Authors
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

require 'webrick'
require 'json'

if ARGV.length < 1 then
    puts "usage: #{$PROGRAM_NAME} port"
    exit(-1)
end

port = Integer(ARGV[0])

server = WEBrick::HTTPServer.new(
    :BindAddress => '*',
    :Port => port,
    :AcceptCallback => -> (s) { s.setsockopt(Socket::IPPROTO_TCP, Socket::TCP_NODELAY, 1) },
)

trap 'INT' do server.shutdown end

server.mount_proc '/health' do |req, res|
    res.status = 200
    res.body = {'status' => 'Movie Details is healthy'}.to_json
    res['Content-Type'] = 'application/json'
end

server.mount_proc '/details' do |req, res|
    pathParts = req.path.split('/')
    headers = get_forward_headers(req)

    begin
        begin
          id = Integer(pathParts[-1])
        rescue
          raise 'please provide numeric movie id'
        end
        details = get_movie_details(id, headers)
        res.body = details.to_json
        res['Content-Type'] = 'application/json'
    rescue => error
        res.body = {'error' => error.message}.to_json
        res['Content-Type'] = 'application/json'
        res.status = 400
    end
end

def get_movie_details(id, headers)
  {
    'id' => id,
    'title' => 'Interstellar',
    'studio' => 'Paramount Pictures, Warner Bros. Pictures',
    'runtime' => 169,
    'genre' => 'Sci-Fi, Drama',
    'language' => 'English'
  }
end

def get_forward_headers(request)
  headers = {}

  incoming_headers = [
      'x-request-id',
      'x-ot-span-context',
      'x-datadog-trace-id',
      'x-datadog-parent-id',
      'x-datadog-sampling-priority',
      'traceparent',
      'tracestate',
      'x-cloud-trace-context',
      'grpc-trace-bin',
      'x-b3-traceid',
      'x-b3-spanid',
      'x-b3-parentspanid',
      'x-b3-sampled',
      'x-b3-flags',
      'sw8',
      'end-user',
      'user-agent',
      'cookie',
      'authorization',
      'jwt'
  ]

  request.each do |header, value|
    if incoming_headers.include? header then
      headers[header] = value
    end
  end

  return headers
end

server.start